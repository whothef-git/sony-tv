package tv.widgetv.home;

/** Repository snapshots; stable identity and order are independent of the display name. */
final class SmartDevice {
    final String id;
    final DeviceType type;
    int order;
    String name;
    ConnectionState connection = ConnectionState.ONLINE;
    PowerState power = PowerState.OFF;
    long disconnectedAt = -1;
    int temperature = 24;
    String mode = "Cool", fanSpeed = "Auto";
    double watts, dailyKwh;
    final PowerState[] outlets = {PowerState.ON, PowerState.OFF, PowerState.ON};
    boolean demo, pending, disabled, daikinWritable;
    String room="", ip="", hardwareId="", mac="", protocol="AUTO", error="";
    long lastSeen, optimisticUntil;
    final String[] outletIds={"","",""};
    final String[] outletNames={"Lampu Tidur","iPhone Charger","Laptop"};

    SmartDevice(String id, DeviceType type, int order, String name) {
        this.id = id; this.type = type; this.order = order; this.name = name;
    }
    SmartDevice copy() {
        SmartDevice d = new SmartDevice(id, type, order, name);
        d.connection=connection; d.power=power; d.disconnectedAt=disconnectedAt;
        d.temperature=temperature; d.mode=mode; d.fanSpeed=fanSpeed;
        d.watts=watts; d.dailyKwh=dailyKwh;
        System.arraycopy(outlets,0,d.outlets,0,3);
        System.arraycopy(outletIds,0,d.outletIds,0,3);System.arraycopy(outletNames,0,d.outletNames,0,3);
        d.demo=demo;d.pending=pending;d.disabled=disabled;d.daikinWritable=daikinWritable;d.room=room;d.ip=ip;d.hardwareId=hardwareId;
        d.mac=mac;d.protocol=protocol;d.error=error;d.lastSeen=lastSeen;d.optimisticUntil=optimisticUntil;
        return d;
    }
    String brand() {
        return type==DeviceType.DAIKIN ? "Daikin" : type==DeviceType.TAPO_P110 ? "Tapo P110" : "Kasa KP303";
    }
    boolean cloud(){return type==DeviceType.DAIKIN&&"GO_DAIKIN".equals(protocol);}
    boolean cached(){return cloud()&&(lastSeen==0||System.currentTimeMillis()-lastSeen>120000);}
    String connectionLabel() { return disabled?"Setup required":cloud()&&connection==ConnectionState.ONLINE&&cached()?"Cloud cached":connection==ConnectionState.ONLINE ? "Online" : demo||connection==ConnectionState.CONNECTING?"Connecting":"Offline"; }
}
