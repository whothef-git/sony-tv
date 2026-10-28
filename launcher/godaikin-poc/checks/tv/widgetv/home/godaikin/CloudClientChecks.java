package tv.widgetv.home.godaikin;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Synthetic mock tests only. These never authenticate or contact real cloud/devices. */
public final class CloudClientChecks {
    private int checks;
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
    interface Action{void run()throws Exception;}
    private void fail(GoDaikinCloudClient.Failure expected,Action action)throws Exception{
        try{action.run();throw new AssertionError("Expected "+expected);}
        catch(GoDaikinCloudClient.CloudException e){check(e.reason==expected,"error category "+expected);check(e.getMessage().equals(expected.name()),"no response secrets in error");}
    }
    private static JSONObject token(String suffix)throws Exception{return new JSONObject().put("AuthenticationResult",new JSONObject()
        .put("IdToken","synthetic-id-"+suffix).put("AccessToken","synthetic-access-"+suffix).put("RefreshToken","synthetic-refresh").put("ExpiresIn",3600));}
    private static JSONObject device()throws Exception{return new JSONObject().put("ThingName","Daikin_SYNTHETIC").put("ACName","Mock TV AC")
        .put("IP","192.0.2.93").put("shadowState",new JSONObject().put("Set_OnOff",0).put("Set_Mode",1).put("Set_Fan",128)
        .put("Sta_IDRoomTemp",26).put("Set_Temp",24).put("eventType","connected").put("updatedOn","2026-10-28 01:00:00")
        .put("version","V1.2.5").put("key","SYNTHETIC-PRIVATE-DEVICE-KEY"));}
    private static JSONObject list(JSONObject... devices)throws Exception{JSONArray array=new JSONArray();for(JSONObject d:devices)array.put(d);return new JSONObject().put("data",array);}
    static final class Request {final String url;final Map<String,String> headers;final JSONObject body;
        Request(String u,Map<String,String> h,JSONObject b){url=u;headers=new HashMap<>(h);body=b;}}
    static final class Mock implements GoDaikinCloudClient.Transport {
        final Deque<Object> responses=new ArrayDeque<>();final List<Request> requests=new ArrayList<>();
        Mock add(int code,JSONObject value){responses.add(new GoDaikinCloudClient.Response(code,value));return this;}
        @Override public GoDaikinCloudClient.Response post(String u,Map<String,String> h,JSONObject b)throws IOException{
            requests.add(new Request(u,h,b));Object next=responses.removeFirst();if(next instanceof IOException)throw (IOException)next;return (GoDaikinCloudClient.Response)next;
        }
    }
    public int run()throws Exception{
        AtomicLong clock=new AtomicLong(1000);
        Mock mock=new Mock().add(200,token("initial")).add(200,list(device()));
        GoDaikinCloudClient client=new GoDaikinCloudClient(mock,clock::get);
        fail(GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED,client::readRegisteredDevices);
        fail(GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED,()->client.authenticate(" ","synthetic"));
        check(mock.requests.isEmpty(),"missing credentials no network");
        client.authenticate("synthetic@example.invalid","synthetic-only-password");
        Request login=mock.requests.get(0);check(login.url.equals(GoDaikinCloudClient.AUTH_URL),"known Cognito endpoint");
        check(login.body.getString("AuthFlow").equals("USER_PASSWORD_AUTH"),"reference login flow");
        check(login.body.getJSONObject("AuthParameters").getString("PASSWORD").equals("synthetic-only-password"),"password sent only to Cognito");
        check(CloudHttpsTransport.allowed(login.url,login.headers,login.body),"login allowlisted");
        GoDaikinCloudClient.Result result=client.readRegisteredDevices();
        check(mock.requests.size()==2,"only login and one list request");
        Request read=mock.requests.get(1);check(read.url.equals(GoDaikinCloudClient.LIST_URL),"known device-list endpoint");
        check(read.headers.get("authorization").equals("synthetic-id-initial"),"raw ID token authorization matches reference");
        check(read.body.getJSONObject("requestData").getInt("type")==1,"read-only list type 1");
        check(!read.body.toString().contains("PASSWORD")&&!read.body.toString().contains("desired"),"read contains no password/desired state");
        check(CloudHttpsTransport.allowed(read.url,read.headers,read.body),"list read allowlisted");
        check(result.devices.size()==1&&result.statusCount()==1,"device and embedded status parsed");
        GoDaikinCloudClient.DeviceStatus d=result.devices.get(0);
        check(d.power==0&&d.roomTemperature==26&&d.targetTemperature==24,"online OFF distinct from unknown");
        check(d.firmware.equals("V1.2.5"),"firmware string retained");
        check(result.summary().contains("Cloud-reported snapshots"),"freshness caveat explicit");
        check(!result.summary().contains("SYNTHETIC-PRIVATE")&&!result.summary().contains("synthetic-id")&&!result.summary().contains("synthetic@example"),"summary excludes keys/tokens/account");
        try{result.devices.clear();throw new AssertionError("mutable list");}catch(UnsupportedOperationException expected){checks++;}
        mock.add(200,list(device()));client.readRegisteredDevices();check(mock.requests.size()==3,"unexpired token reused");
        clock.addAndGet(3300001);
        JSONObject refresh=token("refreshed");refresh.getJSONObject("AuthenticationResult").remove("RefreshToken");
        mock.add(200,refresh).add(200,list(device()));client.readRegisteredDevices();
        Request renew=mock.requests.get(3);check(renew.body.getString("AuthFlow").equals("REFRESH_TOKEN_AUTH"),"expiry refresh flow");
        check(renew.body.getJSONObject("AuthParameters").length()==1&&!renew.body.toString().contains("PASSWORD"),"refresh uses token only");
        check(CloudHttpsTransport.allowed(renew.url,renew.headers,renew.body),"refresh allowlisted");
        check(mock.requests.get(4).headers.get("authorization").equals("synthetic-id-refreshed"),"refreshed ID token used");
        clock.addAndGet(3300001);mock.add(400,new JSONObject().put("__type","NotAuthorizedException"));
        fail(GoDaikinCloudClient.Failure.AUTH_REJECTED,client::readRegisteredDevices);
        int sent=mock.requests.size();fail(GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED,client::readRegisteredDevices);check(mock.requests.size()==sent,"refresh failure clears session no password retry");
        client.close();fail(GoDaikinCloudClient.Failure.CLOSED,()->client.authenticate("x","y"));
        Mock bad=new Mock().add(400,new JSONObject().put("__type","UserNotFoundException").put("message","secret account details"));
        fail(GoDaikinCloudClient.Failure.AUTH_REJECTED,()->new GoDaikinCloudClient(bad,clock::get).authenticate("x","y"));
        Mock challenge=new Mock().add(200,new JSONObject().put("ChallengeName","SMS_MFA").put("Session","sensitive-session"));
        fail(GoDaikinCloudClient.Failure.CHALLENGE_REQUIRED,()->new GoDaikinCloudClient(challenge,clock::get).authenticate("x","y"));check(challenge.requests.size()==1,"challenge stops without further requests");
        Mock flow=new Mock().add(400,new JSONObject().put("__type","InvalidParameterException"));
        fail(GoDaikinCloudClient.Failure.AUTH_FLOW_UNSUPPORTED,()->new GoDaikinCloudClient(flow,clock::get).authenticate("x","y"));
        Mock limited=new Mock().add(429,new JSONObject());fail(GoDaikinCloudClient.Failure.RATE_LIMITED,()->new GoDaikinCloudClient(limited,clock::get).authenticate("x","y"));
        check(limited.requests.size()==1,"no automatic rate-limit retries");
        Mock network=new Mock();network.responses.add(new java.net.SocketTimeoutException("sensitive detail"));
        fail(GoDaikinCloudClient.Failure.NETWORK,()->new GoDaikinCloudClient(network,clock::get).authenticate("x","y"));check(network.requests.size()==1,"no timeout retry");
        Mock tls=new Mock();tls.responses.add(new javax.net.ssl.SSLHandshakeException("untrusted certificate"));
        fail(GoDaikinCloudClient.Failure.TLS,()->new GoDaikinCloudClient(tls,clock::get).authenticate("x","y"));
        Mock malformed=new Mock().add(200,new JSONObject());fail(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE,()->new GoDaikinCloudClient(malformed,clock::get).authenticate("x","y"));
        JSONObject badToken=token("bad");badToken.getJSONObject("AuthenticationResult").put("ExpiresIn",0);
        Mock zeroTtl=new Mock().add(200,badToken);fail(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE,()->new GoDaikinCloudClient(zeroTtl,clock::get).authenticate("x","y"));
        Mock denied=new Mock().add(200,token("denied")).add(403,new JSONObject());GoDaikinCloudClient deny=new GoDaikinCloudClient(denied,clock::get);deny.authenticate("x","y");
        fail(GoDaikinCloudClient.Failure.API_REJECTED,deny::readRegisteredDevices);fail(GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED,deny::readRegisteredDevices);
        fail(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE,()->GoDaikinCloudClient.parseDevices(new JSONObject().put("error","denied"),0));
        check(GoDaikinCloudClient.parseDevices(list(),0).devices.isEmpty(),"empty account not invented demo devices");
        check(GoDaikinCloudClient.parseDevices(list(device(),device()),0).devices.size()==1,"stable cloud ID deduplication");
        JSONObject noShadow=new JSONObject().put("ThingName","Daikin_MISSING");
        GoDaikinCloudClient.DeviceStatus missing=GoDaikinCloudClient.parseDevices(list(noShadow),0).devices.get(0);
        check(missing.power==null&&missing.roomTemperature==null&&!missing.hasStatus,"missing shadow stays unknown");
        JSONObject unknown=device();unknown.getJSONObject("shadowState").put("Set_OnOff",3).put("Set_Mode",255).put("Sta_IDRoomTemp","bad");
        GoDaikinCloudClient.DeviceStatus unfamiliar=GoDaikinCloudClient.parseDevices(list(unknown),0).devices.get(0);
        check(unfamiliar.power==null&&unfamiliar.mode==255&&unfamiliar.roomTemperature==null,"unknown values not silently OFF or invalid temperature");
        JSONObject noId=device();noId.remove("ThingName");fail(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE,()->GoDaikinCloudClient.parseDevices(list(noId),0));
        JSONObject shadowString=device().put("shadowState","unexpected");fail(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE,()->GoDaikinCloudClient.parseDevices(list(shadowString),0));
        JSONArray huge=new JSONArray();for(int i=0;i<=GoDaikinCloudClient.MAX_DEVICES;i++)huge.put(device());
        fail(GoDaikinCloudClient.Failure.MALFORMED_RESPONSE,()->GoDaikinCloudClient.parseDevices(new JSONObject().put("data",huge),0));
        check(!CloudHttpsTransport.allowed(GoDaikinCloudClient.LIST_URL.replace("https:","http:"),read.headers,read.body),"cleartext destination blocked");
        check(!CloudHttpsTransport.allowed(GoDaikinCloudClient.LIST_URL.replace("gethomepageinfowithsubscription","publishdevicestate"),read.headers,read.body),"publish endpoint blocked");
        JSONObject writeBody=new JSONObject().put("requestData",new JSONObject().put("type",3).put("value","x").put("payload",new JSONObject().put("state",new JSONObject().put("desired",new JSONObject()))));
        check(!CloudHttpsTransport.allowed(GoDaikinCloudClient.LIST_URL,read.headers,writeBody),"desired-state payload blocked");
        JSONObject extra=new JSONObject(read.body.toString());extra.getJSONObject("requestData").put("payload",new JSONObject());check(!CloudHttpsTransport.allowed(read.url,read.headers,extra),"extra read payload blocked");
        check(!CloudHttpsTransport.allowed(GoDaikinCloudClient.AUTH_URL,login.headers,new JSONObject(login.body.toString()).put("AuthFlow","CUSTOM_AUTH")),"other auth flows blocked");
        check(!CloudHttpsTransport.allowed(GoDaikinCloudClient.AUTH_URL,login.headers,new JSONObject(login.body.toString()).put("ClientId","other")),"other Cognito clients blocked");
        check(Arrays.stream(GoDaikinCloudClient.class.getMethods()).noneMatch(m->m.getName().startsWith("set")||m.getName().startsWith("turn")||m.getName().equals("control")),"no public control surface");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS: "+new CloudClientChecks().run()+" synthetic mock cloud checks. No real cloud or hardware requests.");}
}
