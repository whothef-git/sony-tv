package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import java.util.Iterator;
import java.util.Map;

/** Exact stable-ID/payload permit; power fields are forbidden outside a separately approved POWER test. */
final class ControlCommandGate implements CloudHttpsTransport.Gate {
    private final String id;
    private JSONObject permit;
    ControlCommandGate(String pinnedId){if(pinnedId==null||pinnedId.isEmpty())throw new IllegalArgumentException();id=pinnedId;}
    synchronized void arm(String target,ControlValidation.Kind kind,JSONObject desired)throws Exception{
        if(!id.equals(target)||permit!=null||!validFields(desired)
            ||kind!=ControlValidation.Kind.POWER&&desired.has("Set_OnOff"))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);
        if(kind==ControlValidation.Kind.FAN&&(desired.length()!=1||!desired.has("Set_Fan")))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.COMMAND_REJECTED);
        if(kind==ControlValidation.Kind.MODE&&!desired.has("Set_Mode"))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.COMMAND_REJECTED);
        if(kind==ControlValidation.Kind.POWER&&!desired.has("Set_OnOff"))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.COMMAND_REJECTED);
        permit=new JSONObject(desired.toString());
    }
    synchronized void revoke(){permit=null;}
    @Override public synchronized boolean allow(String url,Map<String,String> headers,JSONObject body){
        if(!GoDaikinCloudClient.STATE_URL.equals(url)||!headers.containsKey("authorization")||body.length()!=1)return false;
        JSONObject d=body.optJSONObject("requestData");
        if(d==null||!id.equals(d.opt("thingName"))||!(d.opt("username") instanceof String)
            ||!(d.opt("key") instanceof String)||d.optString("key").isEmpty())return false;
        if(Integer.valueOf(1).equals(d.opt("type")))return d.length()==4;
        JSONObject p=d.optJSONObject("payload"),s=p==null?null:p.optJSONObject("state"),v=s==null?null:s.optJSONObject("desired");
        if(!Integer.valueOf(3).equals(d.opt("type"))||d.length()!=5||p==null||p.length()!=1||s==null||s.length()!=1
            ||permit==null||!same(permit,v))return false;
        permit=null;return true;
    }
    static boolean same(JSONObject a,JSONObject b){if(a==null||b==null||a.length()!=b.length())return false;
        Iterator<String> keys=a.keys();while(keys.hasNext()){String k=keys.next();if(!a.opt(k).equals(b.opt(k)))return false;}return true;}
    static boolean validFields(JSONObject d){
        if(d==null||d.length()==0||d.length()>4)return false;
        Iterator<String> keys=d.keys();while(keys.hasNext()){
            String key=keys.next();Object value=d.opt(key);if(!(value instanceof Integer))return false;int n=(Integer)value;
            if("Set_Temp".equals(key)){if(n<16||n>31)return false;}
            else if("Set_Fan".equals(key)){if(n!=2&&n!=4&&n!=8&&n!=128)return false;}
            else if("Set_Mode".equals(key)){if(n!=1&&n!=2&&n!=4)return false;}
            else if("Set_OnOff".equals(key)){if(n!=0&&n!=1)return false;}
            else return false;
        }return true;
    }
}
