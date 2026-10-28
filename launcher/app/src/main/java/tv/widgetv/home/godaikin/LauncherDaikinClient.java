package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import android.os.SystemClock;

/** Small release adapter over the validated cloud client. No POC UI or automatic test/restoration. */
public final class LauncherDaikinClient {
    private final GoDaikinCloudClient cloud;
    private final Gate gate=new Gate();
    private final String user,password;
    private String pinned;
    private boolean authenticated;
    public LauncherDaikinClient(String user,String password,String pinned){this.user=user;this.password=password;this.pinned=pinned;
        cloud=new GoDaikinCloudClient(new CloudHttpsTransport(gate),SystemClock::elapsedRealtime);}
    public static String identity(String raw){try{
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder("godaikin:");for(byte n:hash)b.append(String.format(java.util.Locale.ROOT,"%02x",n&255));return b.toString();
    }catch(Exception e){throw new IllegalStateException();}}
    public static long reportedAt(GoDaikinCloudClient.DeviceStatus d){try{return java.time.LocalDateTime.parse(d.updatedOn,TemperatureValidation.STAMP)
        .atZone(TemperatureValidation.CLOUD_ZONE).toInstant().toEpochMilli();}catch(Exception e){return 0;}}
    public synchronized List<GoDaikinCloudClient.DeviceStatus> readAll()throws Exception{
        if(!authenticated){cloud.authenticate(user,password);authenticated=true;}
        try{
            List<GoDaikinCloudClient.DeviceStatus> result=cloud.readRegisteredDevices().devices;
            if(pinned==null||pinned.isEmpty()){
                // One-time binding to the same user-verified TV registration; all later writes use its full stable ID.
                GoDaikinCloudClient.DeviceStatus candidate=null;
                for(GoDaikinCloudClient.DeviceStatus d:result)if("TV".equalsIgnoreCase(d.name)&&"192.168.50.93".equals(d.ip)&&"V1.2.5".equals(d.firmware)){
                    if(candidate!=null)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);candidate=d;
                }
                if(candidate!=null)pinned=candidate.id;
            }
            gate.id=pinned;return result;
        }catch(GoDaikinCloudClient.CloudException e){if(e.reason==GoDaikinCloudClient.Failure.AUTH_REJECTED||e.reason==GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED||e.reason==GoDaikinCloudClient.Failure.API_REJECTED)authenticated=false;throw e;}
    }
    public synchronized String binding(){return pinned;}
    public synchronized boolean writable(GoDaikinCloudClient.DeviceStatus d){return pinned!=null&&pinned.equals(d.id);}
    private GoDaikinCloudClient.DeviceStatus target(String hash)throws Exception{
        GoDaikinCloudClient.DeviceStatus listed=null;for(GoDaikinCloudClient.DeviceStatus d:readAll())if(identity(d.id).equals(hash))listed=d;
        if(listed==null||!writable(listed))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);
        GoDaikinCloudClient.DeviceStatus actual=cloud.readTargetState(listed.id);
        if(!java.util.Objects.equals(listed.power,actual.power)||!java.util.Objects.equals(listed.mode,actual.mode)
            ||!java.util.Objects.equals(listed.fan,actual.fan)||!java.util.Objects.equals(listed.targetTemperature,actual.targetTemperature))
            throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.STALE_STATE);
        return actual;
    }
    private static boolean fresh(GoDaikinCloudClient.DeviceStatus d){long age=System.currentTimeMillis()-reportedAt(d);
        return "connected".equals(d.event)&&d.stateVersion!=null&&d.stateVersion>0&&age>=-5000&&age<=120000;}
    public synchronized GoDaikinCloudClient.DeviceStatus command(String hash,String field,int value,boolean relative,BooleanSupplier active)throws Exception{
        GoDaikinCloudClient.DeviceStatus before=null;
        for(int i=0;i<40;i++){
            if(!active.getAsBoolean())throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.CLOSED);
            try{before=target(hash);if(!"connected".equals(before.event))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.OFFLINE);
                if(fresh(before))break;before=null;
            }catch(GoDaikinCloudClient.CloudException e){if(e.reason!=GoDaikinCloudClient.Failure.STALE_STATE)throw e;before=null;}
            Thread.sleep(15000);
        }
        if(before==null)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.STALE_STATE);
        if(!field.equals("Set_OnOff")&&!Integer.valueOf(1).equals(before.power))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.STATE_CHANGED);
        if(relative){if(!field.equals("Set_Temp")||before.targetTemperature==null||before.targetTemperature!=Math.rint(before.targetTemperature)||Math.abs(value)!=1)throw new IllegalArgumentException();
            value=Math.max(16,Math.min(31,before.targetTemperature.intValue()+value));}
        JSONObject desired=new JSONObject().put(field,value);
        if(!ControlCommandGate.validFields(desired)||!active.getAsBoolean())throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.COMMAND_REJECTED);
        Object original=field.equals("Set_Temp")?before.targetTemperature:field.equals("Set_Mode")?before.mode:field.equals("Set_Fan")?before.fan:before.power;
        if(original instanceof Number&&((Number)original).doubleValue()==value)return before;
        gate.permit=new JSONObject(desired.toString());
        try{cloud.submitDesired(before.id,desired);}catch(Exception e){/* Ambiguous/rejected response: reconcile reads, never resend. */}finally{gate.permit=null;}
        GoDaikinCloudClient.DeviceStatus candidate=null;
        for(int i=0;i<8;i++){
            Thread.sleep(7000);
            try{GoDaikinCloudClient.DeviceStatus actual=target(hash);
                Object got=field.equals("Set_Temp")?actual.targetTemperature:field.equals("Set_Mode")?actual.mode:field.equals("Set_Fan")?actual.fan:actual.power;
                if(fresh(actual)&&got instanceof Number&&((Number)got).doubleValue()==value&&actual.stateVersion>before.stateVersion&&reportedAt(actual)>reportedAt(before)){
                    if(candidate!=null&&actual.stateVersion>=candidate.stateVersion&&reportedAt(actual)>=reportedAt(candidate))return actual;candidate=actual;
                }else candidate=null;
            }catch(Exception e){candidate=null;}
        }
        throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.STALE_STATE);
    }
    private static final class Gate implements CloudHttpsTransport.Gate {
        String id;JSONObject permit;
        public boolean allow(String url,Map<String,String> headers,JSONObject body){
            if(id==null||!GoDaikinCloudClient.STATE_URL.equals(url)||body.length()!=1||!headers.containsKey("authorization"))return false;
            JSONObject d=body.optJSONObject("requestData");if(d==null||!id.equals(d.opt("thingName"))||!(d.opt("username") instanceof String)||!(d.opt("key") instanceof String))return false;
            if(Integer.valueOf(1).equals(d.opt("type")))return d.length()==4;
            JSONObject p=d.optJSONObject("payload"),s=p==null?null:p.optJSONObject("state"),v=s==null?null:s.optJSONObject("desired");
            if(!Integer.valueOf(3).equals(d.opt("type"))||d.length()!=5||p==null||p.length()!=1||s==null||s.length()!=1||v==null||v.length()!=1
                ||!ControlCommandGate.same(permit,v))return false;
            permit=null;return true;
        }
    }
}
