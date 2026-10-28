package tv.widgetv.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Android storage/lifecycle integration tests with mock drivers/discovery. Never uses real devices. */
public final class SmartHomeIntegrationChecks extends Instrumentation {
    private int checks;
    private RealSmartHomeRepository repository;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private void await(BooleanSupplier condition,String label)throws Exception{long until=System.currentTimeMillis()+6000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<until)Thread.sleep(25);check(condition.getAsBoolean(),label);}
    static final class IsolatedContext extends ContextWrapper {
        final String prefix="phase2-test-"+java.util.UUID.randomUUID()+"-";final List<String> files=new ArrayList<>();
        IsolatedContext(Context base){super(base);}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){String file=prefix+name;if(!files.contains(file))files.add(file);return super.getSharedPreferences(file,mode);}
        void clean(){for(String file:files)getBaseContext().deleteSharedPreferences(file);}
    }
    static final class MockDevices implements SmartDeviceDriver,RealSmartHomeRepository.Discovery {
        volatile boolean reachable=true,network=true,invalidCredentials;
        volatile String firstIp="192.168.50.201";
        final AtomicBoolean power=new AtomicBoolean(true);final AtomicInteger writes=new AtomicInteger();
        final PowerState[] outlets={PowerState.ON,PowerState.OFF,PowerState.ON};
        public List<SmartDevice> discover(List<SmartDevice> saved)throws Exception{
            if(!network)throw new java.io.IOException("TV network is disconnected");List<SmartDevice> list=new ArrayList<>();
            for(int i=0;i<3;i++){SmartDevice d=DeviceRegistry.blank(i==2?DeviceType.KASA_KP303:DeviceType.TAPO_P110,"mock",i);d.ip=i==0?firstIp:"192.168.50."+(202+i);d.mac="aabbccddee0"+(i+1);d.hardwareId="mock-"+i;d.protocol=i==2?"XOR":"KLAP2";list.add(d);}return list;
        }
        public SmartDevice read(SmartDevice d)throws Exception{
            if(!network)throw new java.io.IOException("TV network is disconnected");if(invalidCredentials)throw new TpLinkSession.ProtocolException("Authentication failed: synthetic test account");if(!reachable&&d.mac.endsWith("01"))throw new java.net.SocketTimeoutException();
            SmartDevice actual=d.copy();actual.connection=ConnectionState.ONLINE;actual.disconnectedAt=-1;actual.lastSeen=System.currentTimeMillis();actual.error="";actual.power=power.get()?PowerState.ON:PowerState.OFF;
            actual.watts=power.get()?58:0;actual.dailyKwh=1.32;
            if(d.type==DeviceType.KASA_KP303){actual.watts=Double.NaN;actual.dailyKwh=Double.NaN;for(int i=0;i<3;i++){actual.outlets[i]=outlets[i];actual.outletIds[i]="mock-outlet-"+i;}}
            return actual;
        }
        public SmartDevice setPower(SmartDevice d,boolean on)throws Exception{writes.incrementAndGet();Thread.sleep(300);power.set(on);return read(d);}
        public SmartDevice setOutlet(SmartDevice d,int index,boolean on)throws Exception{writes.incrementAndGet();Thread.sleep(100);outlets[index]=on?PowerState.ON:PowerState.OFF;return read(d);}
    }
    @Override public void onStart(){Bundle output=new Bundle();IsolatedContext context=new IsolatedContext(getTargetContext());MockDevices mock=new MockDevices();AtomicLong clock=new AtomicLong(1000);
        try{
            SmartCredentialStore vault=new SmartCredentialStore(context);vault.save(SmartCredentialStore.ACCOUNT,"synthetic@example.invalid","synthetic-only-password");
            check(vault.load("new-device").password.equals("synthetic-only-password"),"encrypted account retrieval");
            String ciphertext=context.getSharedPreferences("smart_credentials_encrypted_v2",0).getAll().toString();check(!ciphertext.contains("synthetic-only-password")&&!ciphertext.contains("synthetic@example.invalid"),"credentials encrypted at rest");
            repository=new RealSmartHomeRepository(context,(d,c)->mock,mock,clock::get);
            check(!repository.isDemo(),"real mode default");check(repository.all().stream().filter(d->d.disabled&&d.type==DeviceType.DAIKIN).count()==5,"unverified Daikin disabled");
            repository.setActive(true);await(()->repository.visible().size()==3&&repository.visible().stream().allMatch(d->d.connection==ConnectionState.ONLINE),"mock discovery and reads");
            String id=repository.visible().stream().filter(d->d.mac.endsWith("01")).findFirst().get().id;
            String strip=repository.visible().stream().filter(d->d.type==DeviceType.KASA_KP303).findFirst().get().id;
            repository.requestScan();Thread.sleep(350);check(repository.all().size()==8,"no duplicate cards after rediscovery");
            int writes=mock.writes.get();repository.togglePower(id);repository.togglePower(id);
            await(()->repository.find(id).pending,"pending published before response");await(()->!repository.find(id).pending&&repository.find(id).power==PowerState.OFF,"confirmed power response");
            check(mock.writes.get()==writes+1,"duplicate command suppressed");check(repository.visible().stream().anyMatch(d->d.id.equals(id)&&d.power==PowerState.OFF),"online OFF visible");
            for(int i=0;i<3;i++){final int outlet=i;PowerState before=repository.find(strip).outlets[i];repository.toggleOutlet(strip,i);await(()->!repository.find(strip).pending&&repository.find(strip).outlets[outlet]!=before,"outlet confirmation "+i);}
            check(Double.isNaN(repository.find(strip).dailyKwh),"no KP303 energy monitoring");
            mock.reachable=false;repository.refreshDevice(id);await(()->repository.find(id).connection==ConnectionState.CONNECTING,"unreachable is connecting");
            check(repository.find(id).power==PowerState.OFF,"disconnection preserves last confirmed power");check(repository.visible().stream().anyMatch(d->d.id.equals(id)),"grace retains card");clock.addAndGet(45001);check(repository.visible().stream().noneMatch(d->d.id.equals(id)),"grace expiry hides card");
            mock.reachable=true;repository.refreshDevice(id);await(()->repository.find(id).connection==ConnectionState.ONLINE,"reconnection restores card");
            int order=repository.find(id).order;mock.firstIp="192.168.50.211";repository.requestScan();await(()->repository.find(id).ip.equals(mock.firstIp)&&repository.find(id).connection==ConnectionState.ONLINE,"new IP rediscovered by stable identity");check(repository.find(id).order==order&&repository.all().size()==8,"new IP preserves ID/order without duplicates");
            mock.invalidCredentials=true;repository.refreshDevice(id);await(()->repository.find(id).error.startsWith("Authentication failed"),"invalid credentials surfaced");check(!repository.isDemo(),"no demo fallback");mock.invalidCredentials=false;
            repository.change(id,"Renamed real device","Bedroom",-1);await(()->repository.find(id).name.equals("Renamed real device")&&repository.find(id).room.equals("Bedroom"),"name and room edits");
            repository.refreshDevice(id);await(()->repository.find(id).connection==ConnectionState.ONLINE,"read after metadata edit");Thread.sleep(150);
            DeviceRegistry disk=new DeviceRegistry(context);check(disk.find(id)!=null&&disk.find(id).name.equals("Renamed real device")&&disk.find(id).room.equals("Bedroom"),"metadata persisted");
            String metadata=context.getSharedPreferences("smart_devices_v2",0).getString("devices","");check(!metadata.contains("synthetic-only-password")&&!metadata.contains("synthetic@example.invalid"),"credentials separate from metadata");
            repository.setActive(false);Thread.sleep(200);int readsBefore=mock.writes.get();repository.togglePower(id);Thread.sleep(200);check(mock.writes.get()==readsBefore,"no device commands while inactive");
            repository.shutdown();repository=new RealSmartHomeRepository(context,(d,c)->mock,mock,clock::get);check(repository.find(id)!=null&&repository.find(id).connection!=ConnectionState.ONLINE,"restart does not label cached state live");check(repository.find(id).name.equals("Renamed real device"),"restart retains configuration");
            repository.setActive(true);await(()->repository.find(id).connection==ConnectionState.ONLINE,"wake/restart rechecks device");
            mock.network=false;for(SmartDevice d:repository.visible())repository.refreshDevice(d.id);await(()->repository.visible().stream().noneMatch(d->d.connection==ConnectionState.ONLINE),"network loss tracked independently");clock.addAndGet(45001);check(repository.visible().isEmpty(),"network loss empty state");
            mock.network=true;for(SmartDevice d:repository.all())if(!d.disabled)repository.refreshDevice(d.id);await(()->repository.visible().size()==3&&repository.visible().stream().allMatch(d->d.connection==ConnectionState.ONLINE),"network recovery");
            check(vault.load(id)!=null,"encrypted account survives repository restart");vault.remove(SmartCredentialStore.ACCOUNT);check(vault.load(id)==null,"credential removal");
            String removed=repository.visible().stream().filter(d->!d.id.equals(id)&&d.type==DeviceType.TAPO_P110).findFirst().get().id;
            SmartDevice removedRecord=repository.find(removed);repository.remove(removed);await(()->repository.find(removed)==null,"remove saved device");
            repository.requestScan();Thread.sleep(350);check(repository.find(removed)==null&&repository.all().size()==7,"discovery does not re-add removed device");
            check(new DeviceRegistry(context).ignored(removedRecord),"removal suppression persisted");
            CountDownLatch duplicate=new CountDownLatch(1);AtomicBoolean rejected=new AtomicBoolean();repository.configure(null,DeviceType.KASA_KP303,"duplicate","",repository.find(id).ip,"XOR",error->{rejected.set(!error.isEmpty());duplicate.countDown();});
            check(duplicate.await(3,TimeUnit.SECONDS)&&rejected.get(),"duplicate manual IP rejected across types");
            output.putString("stream","PASS: "+checks+" Android integration checks using mock devices/discovery. No real hardware controls tested.\n");finish(Activity.RESULT_OK,output);
        }catch(Throwable e){output.putString("stream","FAIL after "+checks+" checks: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");finish(Activity.RESULT_CANCELED,output);}
        finally{if(repository!=null)repository.shutdown();context.clean();}
    }
}
