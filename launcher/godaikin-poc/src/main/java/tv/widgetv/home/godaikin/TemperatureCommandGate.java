package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import java.util.Map;

/** Per-session stable-ID scope and single-use write permits. Starts read-only; never persisted armed. */
final class TemperatureCommandGate implements CloudHttpsTransport.Gate {
    private String targetId;
    private Integer permittedTemperature;
    synchronized void bind(String stableId){if(stableId==null||stableId.isEmpty())throw new IllegalArgumentException();
        if(targetId!=null&&!targetId.equals(stableId))throw new IllegalStateException();targetId=stableId;}
    synchronized void permitOnce(String stableId,int temperature)throws GoDaikinCloudClient.CloudException{
        if(!stableId.equals(targetId)||temperature<16||temperature>31||permittedTemperature!=null)
            throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);
        permittedTemperature=temperature;
    }
    synchronized void revoke(){permittedTemperature=null;}
    @Override public synchronized boolean allow(String url,Map<String,String> headers,JSONObject body){
        if(!GoDaikinCloudClient.STATE_URL.equals(url)||targetId==null||!headers.containsKey("authorization")||body.length()!=1)return false;
        JSONObject d=body.optJSONObject("requestData");
        if(d==null||!targetId.equals(d.opt("thingName"))||!(d.opt("username") instanceof String)||!(d.opt("key") instanceof String)||d.optString("key").isEmpty())return false;
        if(Integer.valueOf(1).equals(d.opt("type")))return d.length()==4;
        if(!Integer.valueOf(3).equals(d.opt("type"))||d.length()!=5||permittedTemperature==null)return false;
        JSONObject payload=d.optJSONObject("payload"),state=payload==null?null:payload.optJSONObject("state"),desired=state==null?null:state.optJSONObject("desired");
        if(payload==null||payload.length()!=1||state==null||state.length()!=1||desired==null||desired.length()!=1
            ||!permittedTemperature.equals(desired.opt("Set_Temp")))return false;
        permittedTemperature=null;return true; // Consumed before network: a failed request cannot be blindly retried.
    }
}
