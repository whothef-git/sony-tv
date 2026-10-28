package tv.widgetv.home;

import org.json.JSONArray;
import org.json.JSONObject;

/** Pure protocol/state parsing, shared by Android transport and host mock tests. */
final class TpLinkState {
    static String mac(String value){return value==null?"":value.replaceAll("[^a-fA-F0-9]","").toLowerCase(java.util.Locale.ROOT);}
    static JSONObject iotRequest(String method,JSONObject params,String child)throws Exception{
        JSONObject request=new JSONObject().put("system",new JSONObject().put(method,params==null?new JSONObject():params));
        if(child!=null){if(child.isEmpty())throw new TpLinkSession.ProtocolException("Outlet identifier is missing");request.put("context",new JSONObject().put("child_ids",new JSONArray().put(child)));}return request;
    }
    static SmartDevice parse(SmartDevice saved,JSONObject info)throws Exception{
        String model=info.optString("model");if(saved.type==DeviceType.TAPO_P110&&!model.startsWith("P110")||saved.type==DeviceType.KASA_KP303&&!model.startsWith("KP303"))throw new TpLinkSession.ProtocolException("IP address belongs to a different/unsupported device model");
        String mac=mac(info.optString("mac",info.optString("mic_mac"))),id=info.optString("device_id",info.optString("deviceId"));
        boolean matchesMac=!saved.mac.isEmpty()&&!mac.isEmpty()&&mac(saved.mac).equals(mac);
        boolean matchesId=!saved.hardwareId.isEmpty()&&!id.isEmpty()&&saved.hardwareId.equals(id);
        if(mac.isEmpty()&&id.isEmpty()||(!saved.mac.isEmpty()||!saved.hardwareId.isEmpty())&&!matchesMac&&!matchesId)throw new TpLinkSession.ProtocolException("Device identity changed at this IP. Rediscover the original device before controlling it.");
        SmartDevice actual=saved.copy();actual.mac=mac.isEmpty()?saved.mac:mac;actual.hardwareId=id.isEmpty()?saved.hardwareId:id;
        actual.connection=ConnectionState.ONLINE;actual.disconnectedAt=-1;actual.lastSeen=System.currentTimeMillis();actual.error="";actual.watts=Double.NaN;actual.dailyKwh=Double.NaN;
        if(saved.type==DeviceType.TAPO_P110){actual.power=info.getBoolean("device_on")?PowerState.ON:PowerState.OFF;return actual;}
        JSONArray children=info.optJSONArray("children");if(children==null||children.length()!=3)throw new TpLinkSession.ProtocolException("Expected three identified KP303 outlets");
        boolean known=!saved.outletIds[0].isEmpty()&&!saved.outletIds[1].isEmpty()&&!saved.outletIds[2].isEmpty();java.util.Set<String> unique=new java.util.HashSet<>();
        for(int i=0;i<3;i++){JSONObject child=children.getJSONObject(i);String childId=child.getString("id");if(childId.isEmpty()||!unique.add(childId))throw new TpLinkSession.ProtocolException("Outlet identifier is missing or duplicated");int index=known?-1:i;
            if(known)for(int n=0;n<3;n++)if(saved.outletIds[n].equals(childId))index=n;
            if(index<0)throw new TpLinkSession.ProtocolException("Outlet identifier set changed. Refresh configuration before controlling outlets.");
            int value=child.getInt("state");if(value!=0&&value!=1)throw new TpLinkSession.ProtocolException("Outlet state is unconfirmed");actual.outletIds[index]=childId;actual.outlets[index]=value==1?PowerState.ON:PowerState.OFF;
        }
        actual.power=java.util.Arrays.asList(actual.outlets).contains(PowerState.ON)?PowerState.ON:PowerState.OFF;return actual;
    }
    static void energy(SmartDevice actual,JSONObject energy){
        actual.watts=Double.NaN;actual.dailyKwh=Double.NaN;if(energy==null)return;
        double power=energy.optDouble("current_power",Double.NaN),daily=energy.optDouble("today_energy",Double.NaN);
        if(Double.isFinite(power)&&power>=0)actual.watts=power/1000.0;if(Double.isFinite(daily)&&daily>=0)actual.dailyKwh=daily/1000.0;
    }
}
