package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Metadata only. Mutations and disk commits are owned by the real repository's worker. */
final class DeviceRegistry {
    private final SharedPreferences prefs;
    final List<SmartDevice> devices=new ArrayList<>();
    private final java.util.Set<String> ignored;
    String storageError="";
    DeviceRegistry(Context context){
        prefs=context.getSharedPreferences("smart_devices_v2",Context.MODE_PRIVATE);
        ignored=new java.util.HashSet<>(prefs.getStringSet("ignored",java.util.Collections.emptySet()));
        try{JSONArray list=new JSONArray(prefs.getString("devices","[]"));
            for(int i=0;i<list.length();i++){
                JSONObject j=list.getJSONObject(i);SmartDevice d=new SmartDevice(j.getString("id"),DeviceType.valueOf(j.getString("type")),j.optInt("order",i),j.getString("name"));
                d.room=j.optString("room");d.ip=j.optString("ip");d.hardwareId=j.optString("hardwareId");d.mac=j.optString("mac");d.protocol=j.optString("protocol","AUTO");
                d.lastSeen=j.optLong("lastSeen");d.power=PowerState.valueOf(j.optString("power","UNKNOWN"));
                d.watts=j.has("watts")?j.optDouble("watts",Double.NaN):Double.NaN;d.dailyKwh=j.has("dailyKwh")?j.optDouble("dailyKwh",Double.NaN):Double.NaN;
                JSONArray outlets=j.optJSONArray("outlets"),ids=j.optJSONArray("outletIds");
                for(int n=0;n<3;n++){d.outlets[n]=outlets==null?PowerState.UNKNOWN:PowerState.valueOf(outlets.optString(n,"UNKNOWN"));d.outletIds[n]=ids==null?"":ids.optString(n);}
                d.temperature=j.optInt("temperature",0);d.mode=j.optString("mode","Unknown");d.fanSpeed=j.optString("fanSpeed","Unknown");
                d.disabled=d.type==DeviceType.DAIKIN&&!d.cloud()||j.optBoolean("disabled");d.connection=ConnectionState.CONNECTING;d.disconnectedAt=android.os.SystemClock.elapsedRealtime();
                if(d.disabled)d.error="GO DAIKIN controller/API compatibility must be verified. Account login alone is insufficient.";
                devices.add(d);
            }
        }catch(Exception e){devices.clear();storageError="Saved device registry could not be read. No devices were discarded from disk.";}
        sort();
    }
    void sort(){devices.sort(Comparator.comparingInt(d->d.order));}
    SmartDevice find(String id){for(SmartDevice d:devices)if(d.id.equals(id))return d;return null;}
    static String mac(String value){return value==null?"":value.replaceAll("[^a-fA-F0-9]","").toLowerCase(java.util.Locale.ROOT);}
    boolean ignored(SmartDevice d){return !d.mac.isEmpty()&&ignored.contains("mac:"+mac(d.mac))||!d.hardwareId.isEmpty()&&ignored.contains("id:"+d.hardwareId)||ignored.contains("ip:"+d.ip);}
    void ignore(SmartDevice d){if(!d.mac.isEmpty())ignored.add("mac:"+mac(d.mac));if(!d.hardwareId.isEmpty())ignored.add("id:"+d.hardwareId);if(d.mac.isEmpty()&&d.hardwareId.isEmpty()&&!d.ip.isEmpty())ignored.add("ip:"+d.ip);}
    SmartDevice match(SmartDevice other){
        for(SmartDevice d:devices)if(d.type==other.type){
            if(!other.mac.isEmpty()&&mac(d.mac).equals(mac(other.mac)))return d;
            if(!other.hardwareId.isEmpty()&&d.hardwareId.equals(other.hardwareId))return d;
            if(d.hardwareId.isEmpty()&&d.mac.isEmpty()&&!other.ip.isEmpty()&&d.ip.equals(other.ip))return d;
        }return null;
    }
    static SmartDevice blank(DeviceType type,String name,int order){
        SmartDevice d=new SmartDevice(UUID.randomUUID().toString(),type,order,name);d.connection=ConnectionState.CONNECTING;d.power=PowerState.UNKNOWN;
        java.util.Arrays.fill(d.outlets,PowerState.UNKNOWN);d.watts=Double.NaN;d.dailyKwh=Double.NaN;d.disconnectedAt=android.os.SystemClock.elapsedRealtime();
        d.disabled=type==DeviceType.DAIKIN;if(d.disabled)d.error="Setup required: GO DAIKIN controller model and supported API are not verified.";return d;
    }
    void save() throws Exception{
        if(!storageError.isEmpty())throw new java.io.IOException(storageError);
        JSONArray data=new JSONArray();for(SmartDevice d:devices){JSONObject j=new JSONObject();
            j.put("id",d.id).put("type",d.type.name()).put("order",d.order).put("name",d.name).put("room",d.room).put("ip",d.ip)
             .put("hardwareId",d.hardwareId).put("mac",d.mac).put("protocol",d.protocol).put("lastSeen",d.lastSeen).put("power",d.power.name()).put("disabled",d.disabled);
            if(d.type==DeviceType.DAIKIN)j.put("temperature",d.temperature).put("mode",d.mode).put("fanSpeed",d.fanSpeed);
            if(Double.isFinite(d.watts))j.put("watts",d.watts);if(Double.isFinite(d.dailyKwh))j.put("dailyKwh",d.dailyKwh);
            JSONArray outlets=new JSONArray(),ids=new JSONArray();for(int i=0;i<3;i++){outlets.put(d.outlets[i].name());ids.put(d.outletIds[i]);}j.put("outlets",outlets).put("outletIds",ids);data.put(j);
        }if(!prefs.edit().putString("devices",data.toString()).putStringSet("ignored",new java.util.HashSet<>(ignored)).commit())throw new java.io.IOException("Device registry could not be saved");
    }
}
