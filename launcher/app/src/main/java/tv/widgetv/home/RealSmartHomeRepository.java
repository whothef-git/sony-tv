package tv.widgetv.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Registry mutations on one worker; bounded network pool; callbacks return to the UI thread. */
final class RealSmartHomeRepository implements SmartHomeRepository {
    private final Context context;
    private final DeviceRegistry registry;
    private final SmartCredentialStore credentials;
    interface Discovery{List<SmartDevice> discover(List<SmartDevice> saved)throws Exception;}
    interface DriverFactory{SmartDeviceDriver create(SmartDevice d,SmartCredentialStore.Credentials c)throws Exception;}
    private final Discovery discovery;
    private final DriverFactory factory;
    private final java.util.function.LongSupplier elapsed;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService network=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners=new CopyOnWriteArrayList<>();
    private final java.util.Set<String> busy=ConcurrentHashMap.newKeySet(),commands=ConcurrentHashMap.newKeySet();
    private final Map<String,SmartDeviceDriver> clients=new ConcurrentHashMap<>();
    private volatile List<SmartDevice> snapshot=Collections.emptyList();
    private volatile String status="Local mode · Scan or add devices in Manage Devices";
    private volatile boolean active,closed,scanning;
    private volatile long generation;
    private ScheduledFuture<?> polling,rediscovery;
    private tv.widgetv.home.godaikin.LauncherDaikinClient daikin;
    private boolean cloudBusy,cloudLoginRejected;
    private long cloudLastAttempt,cloudEpoch;
    RealSmartHomeRepository(Context c){this(c,null,null,SystemClock::elapsedRealtime);}
    RealSmartHomeRepository(Context c,DriverFactory factory,Discovery discovery,java.util.function.LongSupplier elapsed){context=c.getApplicationContext();registry=new DeviceRegistry(c);credentials=new SmartCredentialStore(c);this.elapsed=elapsed;
        this.factory=factory==null?(d,secret)->new TpLinkDeviceClient(context,d,secret):factory;this.discovery=discovery==null?new DeviceDiscoveryManager(context)::discover:discovery;
        for(SmartDevice d:registry.devices)if(d.connection!=ConnectionState.ONLINE)d.disconnectedAt=elapsed.getAsLong();
        if(registry.devices.isEmpty()&&registry.storageError.isEmpty()){
            String[] names={"Master Bedroom AC","TV AC","Bilik Ibu AC","Bilik Study AC","Dining AC"};
            for(int i=0;i<names.length;i++)registry.devices.add(DeviceRegistry.blank(DeviceType.DAIKIN,names[i],i));
        }publish();worker.execute(this::save);
    }
    public boolean isDemo(){return false;}
    boolean hasAccount(){return credentials.hasAccount();}
    boolean hasDaikinAccount(){return credentials.hasDaikin();}
    public String statusText(){return status;}
    public List<SmartDevice> all(){List<SmartDevice> list=new ArrayList<>();for(SmartDevice d:snapshot)list.add(d.copy());return list;}
    public SmartDevice find(String id){for(SmartDevice d:snapshot)if(d.id.equals(id))return d.copy();return null;}
    public List<SmartDevice> visible(){List<SmartDevice> out=new ArrayList<>();long now=elapsed.getAsLong();for(SmartDevice d:snapshot)if(!d.disabled&&(d.cloud()||d.connection==ConnectionState.ONLINE||d.disconnectedAt>=0&&now-d.disconnectedAt<OFFLINE_GRACE_MS))out.add(d.copy());return out;}
    public long nextVisibilityDelay(){long next=Long.MAX_VALUE,now=elapsed.getAsLong();for(SmartDevice d:snapshot)if(!d.disabled&&d.connection!=ConnectionState.ONLINE&&d.disconnectedAt>=0){long delay=OFFLINE_GRACE_MS-(now-d.disconnectedAt);if(delay>0)next=Math.min(next,delay);}return next==Long.MAX_VALUE?-1:next;}
    public void addListener(Runnable l){if(!listeners.contains(l))listeners.add(l);}
    public void removeListener(Runnable l){listeners.remove(l);}
    private void publish(){List<SmartDevice> list=new ArrayList<>();registry.sort();for(SmartDevice d:registry.devices)list.add(d.copy());snapshot=Collections.unmodifiableList(list);main.post(()->{if(!closed)for(Runnable l:listeners)l.run();});}
    private void save(){try{registry.save();}catch(Exception e){status="Device metadata could not be saved; check storage.";}publish();}
    public void setActive(boolean on){if(closed||active==on)return;active=on;generation++;
        worker.execute(()->{if(polling!=null)polling.cancel(false);if(rediscovery!=null)rediscovery.cancel(false);
            clients.clear();commands.clear();for(SmartDevice d:registry.devices)if(d.pending){d.pending=false;d.error="Command interrupted by TV sleep/navigation. Refresh to confirm actual state.";}
            if(active){polling=worker.scheduleAtFixedRate(this::pollAll,0,10,TimeUnit.SECONDS);rediscovery=worker.scheduleAtFixedRate(this::scanNow,0,30,TimeUnit.SECONDS);}publish();});
    }
    void scanNow(){if(closed||scanning)return;scanning=true;long tag=generation;status="Scanning local network…";publish();List<SmartDevice> saved=all();
        network.execute(()->{List<SmartDevice> found=null;String error=null;try{found=discovery.discover(saved);}catch(Exception e){error=safeError(e);}final List<SmartDevice> result=found;final String problem=error;
            if(closed)return;worker.execute(()->{scanning=false;if(closed||tag!=generation)return;
                if(problem!=null){status=problem;publish();return;}
                for(SmartDevice candidate:result){SmartDevice existing=registry.match(candidate);
                    if(existing==null&&registry.ignored(candidate))continue;
                    if(existing==null){long plugs=registry.devices.stream().filter(d->d.type==DeviceType.TAPO_P110).count();candidate.name=candidate.type==DeviceType.TAPO_P110?(plugs==0?"Fan TV":plugs==1?"Fan Dining":"P110 "+candidate.mac):"Kasa Power Strip · Lampu Tidur";candidate.order=registry.devices.size();registry.devices.add(candidate);existing=candidate;}
                    else {if(!existing.ip.equals(candidate.ip)||!existing.protocol.equals(candidate.protocol))clients.remove(existing.id);existing.ip=candidate.ip;existing.protocol=candidate.protocol;
                        if(!candidate.mac.isEmpty())existing.mac=candidate.mac;if(existing.hardwareId.isEmpty())existing.hardwareId=candidate.hardwareId;
                    }
                    if(existing.connection!=ConnectionState.ONLINE&&existing.disconnectedAt<0)existing.disconnectedAt=elapsed.getAsLong();
                }
                status="LAN devices · "+result.size()+" discovery responses · GO DAIKIN "+(credentials.hasDaikin()?"cloud account saved":"setup in Manage Devices");save();pollAll();
            });
        });
    }
    void requestScan(){if(!closed)worker.execute(this::scanNow);}
    private void pollAll(){if(!active||closed)return;for(SmartDevice d:new ArrayList<>(registry.devices))if(d.type!=DeviceType.DAIKIN&&!d.disabled&&!d.ip.isEmpty()&&!commands.contains(d.id))launch(d.id,null);pollDaikin();}
    void refreshDevice(String id){if(!closed)worker.execute(()->{SmartDevice d=registry.find(id);if(d!=null&&d.type==DeviceType.DAIKIN){cloudLastAttempt=0;cloudLoginRejected=false;pollDaikin();}else{clients.remove(id);launch(id,null);}});}
    private interface Command{SmartDevice run(SmartDeviceDriver client,SmartDevice device)throws Exception;}
    private void launch(String id,Command command){
        SmartDevice current=registry.find(id);if(closed||current==null||current.disabled){commands.remove(id);return;}
        if(!active){current.error="Open Smart Home to connect to devices.";current.pending=false;commands.remove(id);publish();return;}
        if(!busy.add(id)){if(command!=null)worker.schedule(()->launch(id,command),150,TimeUnit.MILLISECONDS);return;}
        SmartDevice input=current.copy();long tag=generation;
        network.execute(()->{SmartDevice output=null;String error=null;
            try{SmartDeviceDriver client=clients.get(id);if(client==null){client=factory.create(input,credentials.load(id));clients.put(id,client);}output=command==null?client.read(input):command.run(client,input);}
            catch(Exception e){clients.remove(id);error=safeError(e);}final SmartDevice actual=output;final String failure=error;
            if(closed)return;worker.execute(()->{SmartDevice d=registry.find(id);if(tag!=generation||d==null||!d.ip.equals(input.ip))clients.remove(id);busy.remove(id);if(command!=null&&tag==generation)commands.remove(id);if(closed||tag!=generation||d==null||!d.ip.equals(input.ip))return;d.pending=false;
                if(actual!=null){d.connection=ConnectionState.ONLINE;d.disconnectedAt=-1;d.lastSeen=actual.lastSeen;d.power=actual.power;d.watts=actual.watts;d.dailyKwh=actual.dailyKwh;d.mac=actual.mac;d.hardwareId=actual.hardwareId;d.error=actual.error;
                    System.arraycopy(actual.outlets,0,d.outlets,0,3);System.arraycopy(actual.outletIds,0,d.outletIds,0,3);
                }else{long now=elapsed.getAsLong();if(d.connection==ConnectionState.ONLINE||d.disconnectedAt<0)d.disconnectedAt=now;
                    d.connection=now-d.disconnectedAt>=OFFLINE_GRACE_MS?ConnectionState.OFFLINE:ConnectionState.CONNECTING;d.error=failure;
                }save();
            });
        });
    }
    private void command(String id,Command command){
        if(closed||!commands.add(id))return;worker.execute(()->{SmartDevice d=registry.find(id);if(d==null||d.disabled||d.connection!=ConnectionState.ONLINE){commands.remove(id);if(d!=null){d.error="Device must be online before controls can be used.";publish();}return;}
            d.pending=true;d.error="";publish();launch(id,command);
        });
    }
    public void togglePower(String id){SmartDevice d=find(id);if(d!=null&&d.type==DeviceType.DAIKIN){if(d.power!=PowerState.UNKNOWN)daikinCommand(id,"Set_OnOff",d.power==PowerState.ON?0:1,false);return;}if(d==null||d.type!=DeviceType.TAPO_P110||d.power==PowerState.UNKNOWN)return;boolean on=d.power!=PowerState.ON;command(id,(client,input)->client.setPower(input,on));}
    public void toggleOutlet(String id,int index){SmartDevice d=find(id);if(d==null||d.type!=DeviceType.KASA_KP303||index<0||index>2||d.outlets[index]==PowerState.UNKNOWN)return;boolean on=d.outlets[index]!=PowerState.ON;command(id,(client,input)->client.setOutlet(input,index,on));}
    public void adjustTemperature(String id,int delta){if(Math.abs(delta)==1)daikinCommand(id,"Set_Temp",delta,true);}
    public void setMode(String id,String mode){int value=mode.equals("Cool")?1:mode.equals("Fan")?2:mode.equals("Dry")?4:-1;if(value>0)daikinCommand(id,"Set_Mode",value,false);}
    public void setFanSpeed(String id,String speed){int value=speed.equals("Low")?2:speed.equals("Medium")?4:speed.equals("High")?8:speed.equals("Auto")?128:-1;if(value>0)daikinCommand(id,"Set_Fan",value,false);}
    private void unsupported(String id){if(!closed)worker.execute(()->{SmartDevice d=registry.find(id);if(d!=null){d.error="GO DAIKIN controls are disabled until controller/API compatibility is verified.";publish();}});}
    private static String normalized(String value){return value.replaceAll("(?i)\\s+AC$","").trim().toLowerCase(java.util.Locale.ROOT);}
    private void applyDaikin(tv.widgetv.home.godaikin.GoDaikinCloudClient.DeviceStatus state){
        String hash=tv.widgetv.home.godaikin.LauncherDaikinClient.identity(state.id);SmartDevice d=null;
        for(SmartDevice item:registry.devices)if(item.type==DeviceType.DAIKIN&&item.hardwareId.equals(hash)){d=item;break;}
        if(d==null)for(SmartDevice item:registry.devices)if(item.type==DeviceType.DAIKIN&&item.hardwareId.isEmpty()&&normalized(item.name).equals(normalized(state.name))){d=item;break;}
        if(d==null){d=DeviceRegistry.blank(DeviceType.DAIKIN,state.name,registry.devices.size());registry.devices.add(d);}
        d.hardwareId=hash;d.protocol="GO_DAIKIN";d.disabled=false;d.daikinWritable=daikin!=null&&daikin.writable(state);d.ip=state.ip;
        d.lastSeen=tv.widgetv.home.godaikin.LauncherDaikinClient.reportedAt(state);long age=System.currentTimeMillis()-d.lastSeen;
        d.connection="connected".equals(state.event)&&age>=-5000&&age<12*60*1000?ConnectionState.ONLINE:ConnectionState.OFFLINE;
        d.power=state.power==null?PowerState.UNKNOWN:state.power==1?PowerState.ON:PowerState.OFF;
        d.temperature=state.targetTemperature==null?0:state.targetTemperature.intValue();
        d.mode=state.mode==null?"Unknown":state.mode==1?"Cool":state.mode==2?"Fan":state.mode==4?"Dry":"Unknown";
        d.fanSpeed=state.fan==null?"Unknown":state.fan==2?"Low":state.fan==4?"Medium":state.fan==8?"High":state.fan==128?"Auto":"Speed "+state.fan;
        d.error=(d.daikinWritable?"TV AC controls":"Read-only AC")+" · Cloud reported "+(d.lastSeen==0?"unknown":java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(d.lastSeen)))
            +(d.cached()?" · Cached snapshot":"");d.disconnectedAt=d.connection==ConnectionState.ONLINE?-1:elapsed.getAsLong();
    }
    private tv.widgetv.home.godaikin.LauncherDaikinClient cloudClient()throws Exception{
        if(daikin==null){SmartCredentialStore.Credentials account=credentials.loadExact(SmartCredentialStore.DAIKIN);
            if(account==null)throw new IllegalStateException();SmartCredentialStore.Credentials binding=credentials.loadExact(SmartCredentialStore.DAIKIN_BINDING);
            daikin=new tv.widgetv.home.godaikin.LauncherDaikinClient(account.username,account.password,binding==null?null:binding.username);}
        return daikin;
    }
    private void pollDaikin(){
        if(!active||closed||cloudBusy||cloudLoginRejected||!credentials.hasDaikin()||elapsed.getAsLong()-cloudLastAttempt<30000)return;
        cloudBusy=true;cloudLastAttempt=elapsed.getAsLong();long tag=generation,epoch=cloudEpoch;
        network.execute(()->{List<tv.widgetv.home.godaikin.GoDaikinCloudClient.DeviceStatus> states=null;String problem=null,binding=null;
            try{tv.widgetv.home.godaikin.LauncherDaikinClient client=cloudClient();states=client.readAll();binding=client.binding();}catch(Exception e){problem=cloudError(e);}
            final List<tv.widgetv.home.godaikin.GoDaikinCloudClient.DeviceStatus> result=states;final String error=problem,pin=binding;
            if(closed)return;worker.execute(()->{cloudBusy=false;if(closed||tag!=generation||epoch!=cloudEpoch)return;
                if(result!=null){try{if(pin!=null)credentials.save(SmartCredentialStore.DAIKIN_BINDING,pin,"");}catch(Exception e){status="GO DAIKIN binding storage failed";}
                    for(tv.widgetv.home.godaikin.GoDaikinCloudClient.DeviceStatus s:result)applyDaikin(s);status="LAN + GO DAIKIN · "+result.size()+" cloud ACs · TV AC controls; others read-only";
                }else{status=error;cloudLoginRejected=error!=null&&error.startsWith("GO DAIKIN login required");for(SmartDevice d:registry.devices)if(d.cloud()){d.connection=ConnectionState.CONNECTING;d.error=error+" · Cached state";}}
                save();
            });
        });
    }
    private void daikinCommand(String id,String field,int value,boolean relative){
        if(closed||!commands.add(id))return;worker.execute(()->launchDaikinCommand(id,field,value,relative));
    }
    private void launchDaikinCommand(String id,String field,int value,boolean relative){
            SmartDevice d=registry.find(id);if(closed||!active||d==null||!d.cloud()||!d.daikinWritable||d.connection!=ConnectionState.ONLINE){commands.remove(id);return;}
            if(cloudBusy){d.pending=true;publish();worker.schedule(()->launchDaikinCommand(id,field,value,relative),250,TimeUnit.MILLISECONDS);return;}
            d.pending=true;d.error="Waiting for fresh cloud state, then actual device confirmation";publish();cloudBusy=true;long tag=generation,epoch=cloudEpoch;String hash=d.hardwareId;
            network.execute(()->{tv.widgetv.home.godaikin.GoDaikinCloudClient.DeviceStatus actual=null;String error=null;
                try{actual=cloudClient().command(hash,field,value,relative,()->active&&!closed&&generation==tag&&cloudEpoch==epoch);}catch(Exception e){error=cloudError(e);}
                final tv.widgetv.home.godaikin.GoDaikinCloudClient.DeviceStatus result=actual;final String problem=error;
                if(closed)return;worker.execute(()->{cloudBusy=false;commands.remove(id);if(closed||tag!=generation||epoch!=cloudEpoch)return;
                    SmartDevice current=registry.find(id);if(current==null)return;current.pending=false;
                    if(result!=null)applyDaikin(result);else current.error=problem+" · State unconfirmed; command will not be repeated";
                    cloudLastAttempt=0;save();
                });
            });
    }
    void saveDaikinCredentials(String user,String password,Consumer<String> done){worker.execute(()->{
        try{credentials.save(SmartCredentialStore.DAIKIN,user,password);cloudEpoch++;daikin=null;cloudLastAttempt=0;cloudLoginRejected=false;status="GO DAIKIN account saved securely";
            main.post(()->done.accept(""));pollDaikin();}catch(Exception e){main.post(()->done.accept("GO DAIKIN credentials could not be saved securely."));}publish();
    });}
    private static String cloudError(Exception e){
        if(e instanceof tv.widgetv.home.godaikin.GoDaikinCloudClient.CloudException){String reason=((tv.widgetv.home.godaikin.GoDaikinCloudClient.CloudException)e).reason.name();
            if(reason.equals("AUTH_REJECTED")||reason.equals("CREDENTIALS_REQUIRED"))return "GO DAIKIN login required; update account in Manage Devices";
            if(reason.equals("STATE_CHANGED"))return "GO DAIKIN state changed or AC is OFF; refresh before control";
            return "GO DAIKIN "+reason.replace('_',' ').toLowerCase(java.util.Locale.ROOT);}
        return "GO DAIKIN connection failed; check internet/account and refresh";
    }
    void configure(String id,DeviceType type,String name,String room,String ip,String protocol,Consumer<String> done){worker.execute(()->{
        try{if(type!=DeviceType.DAIKIN)LanDeviceHttp.validate(ip);SmartDevice d=id==null?DeviceRegistry.blank(type,name,registry.devices.size()):registry.find(id);if(d==null)throw new IllegalArgumentException();
            for(SmartDevice other:registry.devices)if(!other.id.equals(id)&&!ip.isEmpty()&&other.ip.equals(ip))throw new TpLinkSession.ProtocolException("A device with this IP is already saved");
            d.name=name.isEmpty()?type.name():name;d.room=room;d.ip=ip;d.protocol=protocol;if(id==null)registry.devices.add(d);clients.remove(d.id);save();main.post(()->done.accept(""));if(!d.disabled)launch(d.id,null);
        }catch(Exception e){main.post(()->done.accept(safeError(e)));}
    });}
    void change(String id,String name,String room,int delta){worker.execute(()->{SmartDevice d=registry.find(id);if(d==null)return;if(name!=null)d.name=name;if(room!=null)d.room=room;
        if(delta!=0){registry.sort();int i=registry.devices.indexOf(d),j=Math.max(0,Math.min(registry.devices.size()-1,i+delta));Collections.swap(registry.devices,i,j);for(int n=0;n<registry.devices.size();n++)registry.devices.get(n).order=n;}save();});}
    void remove(String id){worker.execute(()->{SmartDevice d=registry.find(id);if(d!=null){registry.ignore(d);registry.devices.remove(d);}clients.remove(id);try{credentials.remove(id);}catch(Exception e){status="Device removed; encrypted credential cleanup failed.";}save();});}
    void saveCredentials(String username,String password,Consumer<String> done){worker.execute(()->{try{credentials.save(SmartCredentialStore.ACCOUNT,username,password);generation++;clients.clear();commands.clear();for(SmartDevice d:registry.devices)d.pending=false;status="TP-Link account saved securely on this TV";publish();main.post(()->done.accept(""));pollAll();}catch(Exception e){main.post(()->done.accept("Credential storage failed. The account was not saved."));}});}
    void clearCredentials(){worker.execute(()->{try{credentials.remove(SmartCredentialStore.ACCOUNT);generation++;commands.clear();clients.clear();for(SmartDevice d:registry.devices)d.pending=false;status="TP-Link credentials removed";}catch(Exception e){status="Could not remove encrypted credentials";}publish();pollAll();});}
    static String safeError(Exception e){
        if(e instanceof TpLinkSession.ProtocolException)return e.getMessage();
        if(e instanceof java.net.SocketTimeoutException)return "Device request timed out. State is unconfirmed; polling will retry without repeating the command.";
        String message=e.getMessage();if(message!=null&&(message.startsWith("Device identity")||message.startsWith("IP address belongs")||message.startsWith("Device did not confirm")||message.startsWith("Outlet did not confirm")||message.startsWith("Expected three")||message.startsWith("This KP303")||message.startsWith("Enter a local")||message.startsWith("Device address must")||message.startsWith("TV network is disconnected")))return message;
        return "Device communication failed. Check network, IP, credentials and firmware compatibility; refresh to retry.";
    }
    public void shutdown(){if(closed)return;active=false;closed=true;generation++;if(polling!=null)polling.cancel(false);if(rediscovery!=null)rediscovery.cancel(false);listeners.clear();clients.clear();network.shutdownNow();worker.shutdown();main.removeCallbacksAndMessages(null);}
}
