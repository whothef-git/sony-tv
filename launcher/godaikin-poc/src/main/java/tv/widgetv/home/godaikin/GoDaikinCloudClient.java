/*
 * Protocol adaptation of doubleukay/godaikin-ha auth.py, api.py and types.py.
 * Copyright 2025 Woon Wai Keen. Licensed under Apache-2.0; see LICENSE-GODAIKIN.
 * Modified 2026-10-28: isolated Java port; Phase 2.2 adds privately scoped temperature validation.
 * No HA dependency, defensive parsing, explicit authentication failures and memory-only tokens.
 */
package tv.widgetv.home.godaikin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Call on a worker thread. No generic endpoint, power, mode or fan control surface. */
public final class GoDaikinCloudClient implements AutoCloseable {
    static final String AUTH_URL = "https://cognito-idp.ap-southeast-1.amazonaws.com/";
    static final String LIST_URL = "https://c7zkf7l933.execute-api.ap-southeast-1.amazonaws.com/prod/gethomepageinfowithsubscription";
    static final String STATE_URL = "https://c7zkf7l933.execute-api.ap-southeast-1.amazonaws.com/prod/publishdevicestate";
    // Public app-client identifier from the reference, not an account credential or client secret.
    static final String CLIENT_ID = "36f6piu770fotfscvhi3jb1vb7";
    static final String AUTH_TARGET = "AWSCognitoIdentityProviderService.InitiateAuth";
    static final int MAX_DEVICES = 100;

    public enum Failure { CREDENTIALS_REQUIRED, AUTH_REJECTED, CHALLENGE_REQUIRED, AUTH_FLOW_UNSUPPORTED,
        AUTH_SERVICE, API_REJECTED, RATE_LIMITED, NETWORK, TLS, MALFORMED_RESPONSE, CLOSED,
        TARGET_MISMATCH, OFFLINE, STALE_STATE, STATE_CHANGED, CONFIRMATION_REQUIRED, PLAN_EXPIRED,
        COMMAND_REJECTED, RESTORATION_REQUIRED }

    public static final class CloudException extends Exception {
        public final Failure reason;
        CloudException(Failure reason) { super(reason.name()); this.reason = reason; }
    }

    /** Must not include response bodies in exception messages or logs. */
    public interface Transport {
        Response post(String url, Map<String,String> headers, JSONObject body) throws IOException, CloudException;
    }

    public static final class Response {
        final int status;
        final JSONObject json;
        public Response(int status, JSONObject json) { this.status=status; this.json=json; }
    }

    /** Whitelisted status. Device key stays private and transient; never enters summaries or journals. */
    public static final class DeviceStatus {
        public final String id, name, ip, firmware, event, updatedOn, subscriptionExpiry;
        public final Integer power, mode, fan;
        public final Double roomTemperature, targetTemperature, outdoorTemperature, watts;
        public final boolean hasStatus;
        public final Long stateVersion;
        public final Map<String,Integer> capabilityFlags;
        public final Integer indoorRpm,compressorOn;
        private final String commandKey;
        private DeviceStatus(JSONObject d) throws JSONException, CloudException {
            id = text(d,"ThingName");
            if (id.isEmpty()) throw new CloudException(Failure.MALFORMED_RESPONSE);
            name=text(d,"ACName"); ip=text(d,"IP"); subscriptionExpiry=text(d,"planExpiredDate");
            JSONObject s=d.optJSONObject("shadowState");
            if(d.has("shadowState")&&!d.isNull("shadowState")&&s==null) throw new CloudException(Failure.MALFORMED_RESPONSE);
            if(s==null)s=new JSONObject();
            power=integer(s,"Set_OnOff",0,1); mode=integer(s,"Set_Mode",0,255); fan=integer(s,"Set_Fan",0,255);
            roomTemperature=number(s,"Sta_IDRoomTemp"); targetTemperature=number(s,"Set_Temp");
            outdoorTemperature=number(s,"Sta_ODAirTemp"); watts=number(s,"Sta_ODPwrCon");
            event=text(s,"eventType"); updatedOn=text(s,"updatedOn"); firmware=text(s,"version");
            commandKey=text(s,"key");
            Object v=s.opt("shadowStateVersion");stateVersion=v instanceof Number&&((Number)v).doubleValue()==((Number)v).longValue()?((Number)v).longValue():null;
            Map<String,Integer> flags=new java.util.HashMap<>();
            for(String flag:new String[]{"Bar_AutoF","Bar_LowF","Bar_AutoM","Bar_CoolM","Bar_DryM","Bar_FanM","Bar_HeatM"}){
                Integer value=integer(s,flag,0,1);if(value!=null)flags.put(flag,value);
            }
            capabilityFlags=Collections.unmodifiableMap(flags);indoorRpm=integer(s,"Sta_IDRPM",0,20000);compressorOn=integer(s,"Sta_CpOnOff",0,1);
            hasStatus=power!=null||roomTemperature!=null||targetTemperature!=null||!updatedOn.isEmpty();
        }
        // Never claims fresh/live connectivity just because a cached shadow says connected.
        public String summary(int index) {
            return "AC "+index+": "+(name.isEmpty()?"Unnamed":name)+" | power="+(power==null?"UNKNOWN":power==1?"ON":"OFF")
                +" | room="+value(roomTemperature)+" C | target="+value(targetTemperature)+" C"
                +" | mode="+(mode==null?"UNKNOWN":mode)+" | event="+(event.isEmpty()?"UNKNOWN":event)
                +" | cloud updated="+(updatedOn.isEmpty()?"UNKNOWN":updatedOn)
                +" | firmware="+(firmware.isEmpty()?"UNKNOWN":firmware);
        }
        private static String value(Double n){return n==null?"UNKNOWN":n.toString();}
    }

    public static final class Result {
        public final List<DeviceStatus> devices;
        public final long receivedAt;
        Result(List<DeviceStatus> devices,long receivedAt){this.devices=Collections.unmodifiableList(devices);this.receivedAt=receivedAt;}
        public int statusCount(){int count=0;for(DeviceStatus d:devices)if(d.hasStatus)count++;return count;}
        public String summary(){
            StringBuilder b=new StringBuilder("REAL CLOUD READ TEST: authentication accepted; registered ACs=")
                .append(devices.size()).append("; ACs with reported status=").append(statusCount())
                .append("\nCloud-reported snapshots; freshness and hardware controls are NOT verified.\nControl commands sent: 0.\n");
            for(int i=0;i<devices.size();i++)b.append(devices.get(i).summary(i+1)).append('\n');
            return b.toString();
        }
    }

    private final Transport transport;
    private final LongSupplier clock;
    private String username, idToken, refreshToken;
    private long refreshAt;
    private boolean closed;
    private String readShape="No targeted state response yet.";
    private final Map<String,DeviceStatus> latest=new java.util.HashMap<>();
    public GoDaikinCloudClient(Transport transport,LongSupplier clock){this.transport=transport;this.clock=clock;}

    public synchronized void authenticate(String user,String password) throws CloudException {
        requireOpen(); clearSession();
        if(user==null||user.trim().isEmpty()||password==null||password.isEmpty())throw new CloudException(Failure.CREDENTIALS_REQUIRED);
        try {
            JSONObject params=new JSONObject().put("USERNAME",user.trim()).put("PASSWORD",password);
            acceptToken(auth("USER_PASSWORD_AUTH",params),false);
            username=user.trim();
        }catch(JSONException e){clearSession();throw new CloudException(Failure.MALFORMED_RESPONSE);}
        catch(CloudException e){clearSession();throw e;}
    }

    public synchronized Result readRegisteredDevices() throws CloudException {
        requireOpen();
        if(idToken==null||username==null)throw new CloudException(Failure.CREDENTIALS_REQUIRED);
        try {
            ensureAuthenticated();
            JSONObject body=new JSONObject().put("requestData",new JSONObject().put("type",1).put("value",username));
            Response response=send(LIST_URL,headers("Content-Type","application/json","authorization",idToken),body);
            if(response.status==429)throw new CloudException(Failure.RATE_LIMITED);
            if(response.status==401||response.status==403){clearSession();throw new CloudException(Failure.API_REJECTED);}
            if(response.status!=200)throw new CloudException(Failure.API_REJECTED);
            Result result=parseDevices(response.json,clock.getAsLong());latest.clear();
            for(DeviceStatus d:result.devices)latest.put(d.id,d);
            return result;
        }catch(JSONException e){throw new CloudException(Failure.MALFORMED_RESPONSE);}
    }

    /** Read request only; intentionally omits upstream's empty desired-state preamble. */
    synchronized DeviceStatus readTargetState(String stableId)throws CloudException{
        try{
            JSONObject request=targetRequest(stableId,1);
            Response r=send(STATE_URL,headers("Content-Type","application/json","authorization",idToken),new JSONObject().put("requestData",request));
            checkApiResponse(r);
            readShape="State response shape: thingName="+r.json.has("thingName")+", Set_Temp="+r.json.has("Set_Temp")
                +", updatedOn="+r.json.has("updatedOn")+", shadowStateVersion="+r.json.has("shadowStateVersion")
                +", reported="+r.json.has("reported")+", state="+r.json.has("state")+", data="+r.json.has("data")+", body="+r.json.has("body");
            String returned=text(r.json,"thingName");
            if(!stableId.equals(returned))throw new CloudException(Failure.TARGET_MISMATCH);
            DeviceStatus old=latest.get(stableId);
            // Some status replies omit the key; preserve the key obtained for this same stable ID.
            if(!r.json.has("key"))r.json.put("key",old.commandKey);
            JSONObject device=new JSONObject().put("ThingName",stableId).put("ACName",old.name).put("IP",old.ip).put("shadowState",r.json);
            DeviceStatus state=new DeviceStatus(device);latest.put(stableId,state);return state;
        }catch(JSONException e){throw new CloudException(Failure.MALFORMED_RESPONSE);}
    }
    synchronized String safeReadShape(){return readShape;}

    /** Package-private: only the explicit validation transaction uses this, guarded by transport. */
    synchronized void submitTemperature(String stableId,int temperature)throws CloudException{
        if(temperature<16||temperature>31)throw new CloudException(Failure.COMMAND_REJECTED);
        try{submitDesired(stableId,new JSONObject().put("Set_Temp",temperature));}
        catch(JSONException e){throw new CloudException(Failure.MALFORMED_RESPONSE);}
    }

    /** Fan/mode/power are accessible only through an exact, armed developer transaction gate. */
    synchronized void submitDesired(String stableId,JSONObject desired)throws CloudException{
        if(!ControlCommandGate.validFields(desired))throw new CloudException(Failure.COMMAND_REJECTED);
        try{
            JSONObject request=targetRequest(stableId,3).put("payload",new JSONObject().put("state",new JSONObject().put("desired",desired)));
            Response r=send(STATE_URL,headers("Content-Type","application/json","authorization",idToken),new JSONObject().put("requestData",request));
            checkApiResponse(r);
            // Only parses explicit rejection. Even a valid response never establishes device success.
            parseCommandResponse(r.json);
        }catch(JSONException e){throw new CloudException(Failure.MALFORMED_RESPONSE);}
    }

    private JSONObject targetRequest(String stableId,int type)throws CloudException,JSONException{
        requireOpen();ensureAuthenticated();DeviceStatus target=latest.get(stableId);
        if(target==null||target.commandKey.isEmpty())throw new CloudException(Failure.TARGET_MISMATCH);
        return new JSONObject().put("type",type).put("username",username).put("thingName",stableId).put("key",target.commandKey);
    }
    private void ensureAuthenticated()throws CloudException,JSONException{
        requireOpen();if(idToken==null||username==null)throw new CloudException(Failure.CREDENTIALS_REQUIRED);
        if(clock.getAsLong()>=refreshAt){
            if(refreshToken==null){clearSession();throw new CloudException(Failure.CREDENTIALS_REQUIRED);}
            try{acceptToken(auth("REFRESH_TOKEN_AUTH",new JSONObject().put("REFRESH_TOKEN",refreshToken)),true);}
            catch(CloudException e){clearSession();throw e;}
        }
    }
    private void checkApiResponse(Response r)throws CloudException{
        if(r.status==429)throw new CloudException(Failure.RATE_LIMITED);
        if(r.status==401||r.status==403){clearSession();throw new CloudException(Failure.API_REJECTED);}
        if(r.status!=200)throw new CloudException(Failure.COMMAND_REJECTED);
    }
    static void parseCommandResponse(JSONObject response)throws CloudException{
        if(response.has("error")||response.has("errorMessage")||response.has("__type")
            ||Boolean.FALSE.equals(response.opt("success"))||response.optInt("statusCode",200)>=400)
            throw new CloudException(Failure.COMMAND_REJECTED);
        // Acknowledgement body is otherwise opaque/unverified; fresh state readback is mandatory.
    }

    private JSONObject auth(String flow,JSONObject params) throws CloudException,JSONException {
        JSONObject body=new JSONObject().put("ClientId",CLIENT_ID).put("AuthFlow",flow).put("AuthParameters",params);
        Response r=send(AUTH_URL,headers("Content-Type","application/x-amz-json-1.1","X-Amz-Target",AUTH_TARGET),body);
        if(r.status==429)throw new CloudException(Failure.RATE_LIMITED);
        if(r.status!=200){
            String type=r.json.optString("__type","");
            if(type.endsWith("TooManyRequestsException"))throw new CloudException(Failure.RATE_LIMITED);
            if(type.endsWith("NotAuthorizedException")||type.endsWith("UserNotFoundException")||type.endsWith("UserNotConfirmedException")||type.endsWith("PasswordResetRequiredException"))
                throw new CloudException(Failure.AUTH_REJECTED);
            if(type.endsWith("InvalidParameterException"))throw new CloudException(Failure.AUTH_FLOW_UNSUPPORTED);
            throw new CloudException(Failure.AUTH_SERVICE);
        }
        if(!r.json.optString("ChallengeName","").isEmpty())throw new CloudException(Failure.CHALLENGE_REQUIRED);
        JSONObject result=r.json.optJSONObject("AuthenticationResult");
        if(result==null)throw new CloudException(Failure.MALFORMED_RESPONSE);
        return result;
    }

    private void acceptToken(JSONObject auth,boolean refresh) throws JSONException,CloudException {
        String id=text(auth,"IdToken"),access=text(auth,"AccessToken"),next=text(auth,"RefreshToken");
        double expiry=auth.optDouble("ExpiresIn",Double.NaN);
        if(id.isEmpty()||access.isEmpty()||!Double.isFinite(expiry)||expiry<=0||expiry>31536000||(!refresh&&next.isEmpty()))
            throw new CloudException(Failure.MALFORMED_RESPONSE);
        idToken=id;
        if(!next.isEmpty())refreshToken=next;
        // Five minutes early, capped at 10% for short mock/service lifetimes. Monotonic clock.
        long ttl=(long)(expiry*1000);refreshAt=clock.getAsLong()+ttl-Math.min(300000,ttl/10);
    }

    private Response send(String url,Map<String,String> headers,JSONObject body)throws CloudException {
        try {Response r=transport.post(url,headers,body);if(r==null||r.json==null)throw new CloudException(Failure.MALFORMED_RESPONSE);return r;}
        catch(javax.net.ssl.SSLException e){throw new CloudException(Failure.TLS);}
        catch(IOException e){throw new CloudException(Failure.NETWORK);}
    }

    static Result parseDevices(JSONObject response,long receivedAt)throws CloudException {
        JSONArray data=response.optJSONArray("data");
        if(data==null||data.length()>MAX_DEVICES)throw new CloudException(Failure.MALFORMED_RESPONSE);
        List<DeviceStatus> devices=new ArrayList<>();Set<String> ids=new HashSet<>();
        try{for(int i=0;i<data.length();i++){
            DeviceStatus d=new DeviceStatus(data.getJSONObject(i));
            if(ids.add(d.id.toLowerCase(java.util.Locale.ROOT)))devices.add(d);
        }}catch(JSONException e){throw new CloudException(Failure.MALFORMED_RESPONSE);}
        return new Result(devices,receivedAt);
    }

    private static String text(JSONObject j,String key) throws CloudException {
        Object value=j.opt(key);if(value==null||value==JSONObject.NULL)return "";
        if(!(value instanceof String)||((String)value).length()>4096)throw new CloudException(Failure.MALFORMED_RESPONSE);
        return (String)value;
    }
    private static Double number(JSONObject j,String key){Object v=j.opt(key);if(!(v instanceof Number))return null;double n=((Number)v).doubleValue();return Double.isFinite(n)?n:null;}
    private static Integer integer(JSONObject j,String key,int min,int max){Double n=number(j,key);return n!=null&&n==Math.rint(n)&&n>=min&&n<=max?n.intValue():null;}
    private void requireOpen()throws CloudException{if(closed)throw new CloudException(Failure.CLOSED);}
    private static Map<String,String> headers(String... pairs){Map<String,String> result=new java.util.HashMap<>();for(int i=0;i<pairs.length;i+=2)result.put(pairs[i],pairs[i+1]);return result;}
    private void clearSession(){username=null;idToken=null;refreshToken=null;refreshAt=0;latest.clear();}
    @Override public synchronized void close(){clearSession();closed=true;}
}
