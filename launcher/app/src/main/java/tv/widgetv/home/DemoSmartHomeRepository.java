package tv.widgetv.home;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Main-thread, in-memory demo only. No discovery, sockets, credentials or network calls. */
final class DemoSmartHomeRepository implements SmartHomeRepository {
    private final Map<String, SmartDevice> devices = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final LongSupplier clock;
    DemoSmartHomeRepository(LongSupplier clock) { this.clock=clock; reset(); }
    void reset() {
        devices.clear();
        String[] names={"Master Bedroom AC","TV AC","Bilik Ibu AC","Bilik Study AC","Dining AC","Fan TV","Fan Dining","Kasa Power Strip · Lampu Tidur"};
        for(int i=0;i<8;i++) {
            DeviceType type=i<5?DeviceType.DAIKIN:i<7?DeviceType.TAPO_P110:DeviceType.KASA_KP303;
            SmartDevice d=new SmartDevice("demo-"+(i+1),type,i,names[i]);
            d.demo=true;
            d.power=i==1||i==4||i==6?PowerState.OFF:PowerState.ON;
            d.temperature=23+i%4;
            d.watts=i==5?58:0; d.dailyKwh=i==5?1.32:i==6?0.24:0;
            devices.put(d.id,d);
        }
        notifyListeners();
    }
    public List<SmartDevice> all() {
        List<SmartDevice> out=new ArrayList<>();
        for(SmartDevice d:devices.values())out.add(d.copy());
        return out;
    }
    public List<SmartDevice> visible() {
        List<SmartDevice> out=new ArrayList<>(); long now=clock.getAsLong();
        for(SmartDevice d:devices.values())
            if(d.connection==ConnectionState.ONLINE || now-d.disconnectedAt<OFFLINE_GRACE_MS)out.add(d.copy());
        return out;
    }
    public SmartDevice find(String id) { SmartDevice d=devices.get(id); return d==null?null:d.copy(); }
    public void addListener(Runnable l) { if(!listeners.contains(l))listeners.add(l); }
    public void removeListener(Runnable l) { listeners.remove(l); }
    private void notifyListeners() { for(Runnable l:new ArrayList<>(listeners))l.run(); }
    public long nextVisibilityDelay() {
        long next=Long.MAX_VALUE,now=clock.getAsLong();
        for(SmartDevice d:devices.values())if(d.connection!=ConnectionState.ONLINE) {
            long delay=OFFLINE_GRACE_MS-(now-d.disconnectedAt);
            if(delay>0)next=Math.min(next,delay);
        }
        return next==Long.MAX_VALUE?-1:next;
    }
    void setConnection(String id,ConnectionState state) {
        SmartDevice d=devices.get(id); if(d==null)return;
        if(state==ConnectionState.ONLINE)d.disconnectedAt=-1;
        else if(d.connection==ConnectionState.ONLINE)d.disconnectedAt=clock.getAsLong();
        d.connection=state; notifyListeners();
    }
    void setAllConnection(ConnectionState state) {
        long now=clock.getAsLong();
        for(SmartDevice d:devices.values()) {
            if(state==ConnectionState.ONLINE)d.disconnectedAt=-1;
            else if(d.connection==ConnectionState.ONLINE)d.disconnectedAt=now;
            d.connection=state;
        }
        notifyListeners();
    }
    private void edit(String id,Consumer<SmartDevice> change) {
        SmartDevice d=devices.get(id);
        if(d==null||d.connection!=ConnectionState.ONLINE)return;
        change.accept(d); notifyListeners();
    }
    public void togglePower(String id) { edit(id,d->{
        d.power=d.power==PowerState.ON?PowerState.OFF:PowerState.ON;
        if(d.type==DeviceType.TAPO_P110)d.watts=d.power==PowerState.ON?58:0;
    }); }
    void setPower(String id,PowerState power) { edit(id,d->{d.power=power;if(d.type==DeviceType.TAPO_P110)d.watts=power==PowerState.ON?58:0;}); }
    public void adjustTemperature(String id,int delta) { edit(id,d->{if(d.type==DeviceType.DAIKIN)d.temperature=Math.max(16,Math.min(30,d.temperature+delta));}); }
    public void setMode(String id,String mode) { if(!java.util.Arrays.asList("Cool","Dry","Fan","Auto").contains(mode))return;edit(id,d->{if(d.type==DeviceType.DAIKIN)d.mode=mode;}); }
    public void setFanSpeed(String id,String speed) { if(!java.util.Arrays.asList("Low","Medium","High","Auto").contains(speed))return;edit(id,d->{if(d.type==DeviceType.DAIKIN)d.fanSpeed=speed;}); }
    public void toggleOutlet(String id,int outlet) { if(outlet<0||outlet>2)return;edit(id,d->{if(d.type==DeviceType.KASA_KP303)d.outlets[outlet]=d.outlets[outlet]==PowerState.ON?PowerState.OFF:PowerState.ON;}); }
}
