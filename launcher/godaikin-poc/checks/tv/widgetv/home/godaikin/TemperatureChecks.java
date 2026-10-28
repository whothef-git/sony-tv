package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.Failure;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.CloudException;

/** Pure synthetic transaction/payload checks. No real network, credentials or device identifiers. */
public final class TemperatureChecks {
    int count;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;}
    interface Action{void run()throws Exception;}
    void fail(Failure reason,Action action)throws Exception{try{action.run();throw new AssertionError("expected "+reason);}catch(CloudException e){check(e.reason==reason,"failure "+reason);}}
    static class Rig implements TemperatureValidation.Device,TemperatureValidation.Journal {
        AtomicLong wall=new AtomicLong(Instant.parse("2026-10-28T09:00:00Z").toEpochMilli()),mono=new AtomicLong(0);
        String id="mock-tv",event="connected";int temp=23,power=1,mode=1,fan=128;
        long version=100,stamp=wall.get();Integer pending;
        JSONObject saved;
        boolean failSave,stale,failAfterSend,reject,failRestore,offlineAfter,wrongAfter,externalMode;
        List<Integer> writes=new ArrayList<>();
        void tick(long ms){wall.addAndGet(ms);mono.addAndGet(ms);}
        String timestamp(){return TemperatureValidation.STAMP.format(Instant.ofEpochMilli(stamp).atZone(TemperatureValidation.CLOUD_ZONE));}
        public TemperatureValidation.State read(){
            if(pending!=null){temp=pending;pending=null;if(!stale){version++;stamp=wall.get();}}
            return new TemperatureValidation.State(wrongAfter&&!writes.isEmpty()?"mock-neighbor":id,offlineAfter&&!writes.isEmpty()?"disconnected":event,timestamp(),
                power,externalMode&&!writes.isEmpty()?4:mode,fan,temp,version);
        }
        public void temperature(int value)throws Exception{
            writes.add(value);if(reject||failRestore&&writes.size()==2)throw new CloudException(Failure.COMMAND_REJECTED);
            pending=value;if(failAfterSend)throw new java.net.SocketTimeoutException("synthetic-only");
        }
        public JSONObject load(){return saved;}
        public void save(JSONObject value)throws Exception{if(failSave)throw new java.io.IOException();saved=new JSONObject(value.toString());}
        TemperatureValidation engine(){return new TemperatureValidation("mock-tv",this,this,mono::get,wall::get,this::tick,s->{});}
    }
    static JSONObject command(String id,int type,int temperature)throws Exception{
        JSONObject d=new JSONObject().put("type",type).put("username","synthetic@example.invalid").put("thingName",id).put("key","synthetic-private-key");
        if(type==3)d.put("payload",new JSONObject().put("state",new JSONObject().put("desired",new JSONObject().put("Set_Temp",temperature))));
        return new JSONObject().put("requestData",d);
    }
    public int run()throws Exception{
        Rig ok=new Rig();TemperatureValidation validation=ok.engine();
        check(ok.writes.isEmpty(),"construction sends nothing");TemperatureValidation.Plan plan=validation.prepare();
        check(ok.writes.isEmpty()&&ok.saved==null,"prepare sends no write or pending journal");
        check(plan.changed==24&&plan.original.temp==23,"one C adjustment prepared");
        check(!plan.preview().contains("mock-tv")&&!plan.preview().contains("synthetic-private-key"),"preview redacts stable ID and key");
        fail(Failure.CONFIRMATION_REQUIRED,()->validation.run(plan,false));check(ok.writes.isEmpty(),"no write without screen approval");
        TemperatureValidation.Outcome result=validation.run(plan,true);
        check(result.changed&&result.restored,"fresh change and restoration confirmed");check(ok.writes.equals(java.util.Arrays.asList(24,23)),"exactly change and compensation writes");
        check(ok.temp==23&&"COMPLETE".equals(ok.saved.getString("phase")),"original restored and complete journal");
        check(ok.saved.getJSONObject("original").getInt("temp")==23,"original recorded");
        check(ok.mono.get()==28000,"two fresh observations per command");
        fail(Failure.CONFIRMATION_REQUIRED,()->validation.run(plan,true));check(ok.writes.size()==2,"used plan cannot repeat write");
        Rig expired=new Rig();TemperatureValidation e=expired.engine();TemperatureValidation.Plan p=e.prepare();expired.tick(TemperatureValidation.PLAN_MS+1);
        fail(Failure.PLAN_EXPIRED,()->e.run(p,true));check(expired.writes.isEmpty(),"expired plan sends nothing");
        Rig slowPrecheck=new Rig(){int reads;@Override public TemperatureValidation.State read(){if(++reads==2){tick(TemperatureValidation.PLAN_MS+1);stamp=wall.get();}return super.read();}};
        TemperatureValidation slow=slowPrecheck.engine();TemperatureValidation.Plan slowPlan=slow.prepare();
        fail(Failure.PLAN_EXPIRED,()->slow.run(slowPlan,true));check(slowPrecheck.writes.isEmpty(),"approval rechecked after slow pre-write reads");
        Rig modified=new Rig();TemperatureValidation m=modified.engine();TemperatureValidation.Plan mp=m.prepare();modified.temp=22;
        fail(Failure.STATE_CHANGED,()->m.run(mp,true));check(modified.writes.isEmpty(),"external target change aborts before command");
        Rig changedMode=new Rig();TemperatureValidation cm=changedMode.engine();TemperatureValidation.Plan cp=cm.prepare();changedMode.mode=4;
        fail(Failure.STATE_CHANGED,()->cm.run(cp,true));check(changedMode.writes.isEmpty(),"external operating mode change aborts");
        Rig offline=new Rig();offline.event="disconnected";fail(Failure.OFFLINE,()->offline.engine().prepare());check(offline.writes.isEmpty(),"offline blocks prepare");
        Rig old=new Rig();old.stamp-=120001;TemperatureValidation oldEngine=old.engine();TemperatureValidation.Plan oldPlan=oldEngine.prepare();
        check(oldPlan.original.temp==23&&old.writes.isEmpty(),"cached snapshot can only prepare a no-write preview");
        fail(Failure.STALE_STATE,()->oldEngine.run(oldPlan,true));check(old.writes.isEmpty(),"stale original never bypasses command freshness");
        Rig heartbeat=new Rig(){int reads;@Override public TemperatureValidation.State read(){if(++reads==3){stamp=wall.get();version++;}return super.read();}};
        heartbeat.stamp-=180000;TemperatureValidation hb=heartbeat.engine();TemperatureValidation.Outcome hbo=hb.run(hb.prepare(),true);
        check(hbo.changed&&hbo.restored&&heartbeat.writes.size()==2,"approved action waits for fresh unchanged original before first write");
        Rig changedWhileWaiting=new Rig(){int reads;@Override public TemperatureValidation.State read(){if(++reads==3){stamp=wall.get();version++;temp=22;}return super.read();}};
        changedWhileWaiting.stamp-=180000;TemperatureValidation cw=changedWhileWaiting.engine();TemperatureValidation.Plan cwp=cw.prepare();
        fail(Failure.STATE_CHANGED,()->cw.run(cwp,true));check(changedWhileWaiting.writes.isEmpty(),"fresh different original does not alter approved payload");
        Rig future=new Rig();future.stamp+=6000;fail(Failure.STALE_STATE,()->future.engine().prepare());
        Rig powerOff=new Rig();powerOff.power=0;fail(Failure.STATE_CHANGED,()->powerOff.engine().prepare());check(powerOff.writes.isEmpty(),"no power-on test for OFF AC");
        Rig badId=new Rig();badId.id="mock-other";fail(Failure.TARGET_MISMATCH,()->badId.engine().prepare());
        Rig noVersion=new Rig();noVersion.version=0;fail(Failure.STALE_STATE,()->noVersion.engine().prepare());
        Rig invalidTemperature=new Rig();invalidTemperature.temp=32;fail(Failure.STATE_CHANGED,()->invalidTemperature.engine().prepare());
        TemperatureValidation.State badDate=new TemperatureValidation.State("mock-tv","connected","2026-02-30 17:00:00",1,1,128,23,100L);
        fail(Failure.STALE_STATE,()->ok.engine().fresh(badDate));
        Rig boundary=new Rig();boundary.temp=31;check(boundary.engine().prepare().changed==30,"upper bound uses minus one");
        Rig low=new Rig();low.temp=16;check(low.engine().prepare().changed==17,"lower bound plus one");
        Rig stale=new Rig();stale.stale=true;TemperatureValidation sv=stale.engine();TemperatureValidation.Outcome staleOutcome=sv.run(sv.prepare(),true);
        check(!staleOutcome.changed&&!staleOutcome.restored,"HTTP-only or optimistic stale echo never success");
        check(stale.writes.size()==1&&TemperatureValidation.pending(stale.saved),"unconfirmed command not retried; recovery journal retained");
        fail(Failure.RESTORATION_REQUIRED,()->stale.engine().prepare());
        Rig versionOnly=new Rig(){@Override public TemperatureValidation.State read(){TemperatureValidation.State s=super.read();return new TemperatureValidation.State(s.id,s.event,
            TemperatureValidation.STAMP.format(Instant.ofEpochMilli(Instant.parse("2026-10-28T09:00:00Z").toEpochMilli()).atZone(TemperatureValidation.CLOUD_ZONE)),s.power,s.mode,s.fan,s.temp,s.version);}};
        TemperatureValidation vo=versionOnly.engine();check(!vo.run(vo.prepare(),true).changed,"new version without newer reported timestamp not confirmed");
        Rig timeOnly=new Rig(){@Override public TemperatureValidation.State read(){TemperatureValidation.State s=super.read();return new TemperatureValidation.State(s.id,s.event,s.stamp,s.power,s.mode,s.fan,s.temp,100L);}};
        TemperatureValidation to=timeOnly.engine();check(!to.run(to.prepare(),true).changed,"new timestamp without newer state version not confirmed");
        Rig ambiguous=new Rig();ambiguous.failAfterSend=true;TemperatureValidation av=ambiguous.engine();TemperatureValidation.Outcome ao=av.run(av.prepare(),true);
        check(ao.changed&&ao.restored&&ambiguous.writes.size()==2,"timeout with real fresh effect uses read confirmation, no blind retry");
        Rig rejected=new Rig();rejected.reject=true;TemperatureValidation rv=rejected.engine();TemperatureValidation.Outcome ro=rv.run(rv.prepare(),true);
        check(!ro.changed&&!ro.restored&&rejected.writes.size()==1,"rejected write not reported success or resent");
        Rig restoreFail=new Rig();restoreFail.failRestore=true;TemperatureValidation rf=restoreFail.engine();TemperatureValidation.Outcome rfo=rf.run(rf.prepare(),true);
        check(rfo.changed&&!rfo.restored&&restoreFail.writes.size()==2,"failed restoration remains incomplete without retry");
        check(TemperatureValidation.pending(restoreFail.saved),"failed restore persisted");restoreFail.failRestore=false;
        fail(Failure.CONFIRMATION_REQUIRED,()->restoreFail.engine().recover(false));
        TemperatureValidation.Outcome recovered=restoreFail.engine().recover(true);check(recovered.restored&&restoreFail.writes.size()==3,"explicit recovery only once");
        Rig storeFailure=new Rig();TemperatureValidation sf=storeFailure.engine();TemperatureValidation.Plan sp=sf.prepare();storeFailure.failSave=true;
        try{sf.run(sp,true);throw new AssertionError("missing journal failure");}catch(java.io.IOException expected){check(storeFailure.writes.isEmpty(),"durable original required before any write");}
        Rig loss=new Rig();loss.offlineAfter=true;TemperatureValidation lv=loss.engine();TemperatureValidation.Outcome lo=lv.run(lv.prepare(),true);
        check(!lo.changed&&loss.writes.size()==1,"offline after command stops uncertain flow");
        Rig wrong=new Rig();wrong.wrongAfter=true;TemperatureValidation wv=wrong.engine();check(!wv.run(wv.prepare(),true).changed&&wrong.writes.size()==1,"different stable ID never accepted");
        Rig settingChange=new Rig();settingChange.externalMode=true;TemperatureValidation sc=settingChange.engine();check(!sc.run(sc.prepare(),true).changed,"unrelated state changes prevent success");
        Map<String,String> headers=new HashMap<>();headers.put("authorization","synthetic-id-token");
        TemperatureCommandGate gate=new TemperatureCommandGate();JSONObject write=command("mock-tv",3,24),read=command("mock-tv",1,0);
        check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,write),"unbound gate blocks all writes");gate.bind("mock-tv");
        check(gate.allow(GoDaikinCloudClient.STATE_URL,headers,read),"bound target read allowed before approval");
        check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,write),"no write permit by default");
        fail(Failure.TARGET_MISMATCH,()->gate.permitOnce("mock-neighbor",24));
        gate.permitOnce("mock-tv",24);
        check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,command("mock-neighbor",3,24)),"other stable ID cannot write");
        JSONObject power=command("mock-tv",3,24);power.getJSONObject("requestData").getJSONObject("payload").getJSONObject("state").getJSONObject("desired").put("Set_OnOff",0);
        check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,power),"power field forbidden even with permit");
        check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,command("mock-tv",3,25)),"unapproved temperature forbidden");
        check(!gate.allow(GoDaikinCloudClient.STATE_URL.replace("https:","http:"),headers,write),"cleartext forbidden");
        check(gate.allow(GoDaikinCloudClient.STATE_URL,headers,write),"exact single approved temperature allowed");
        check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,write),"permit consumed before connection and cannot retry");
        gate.permitOnce("mock-tv",23);gate.revoke();check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,command("mock-tv",3,23)),"revoked permit blocked");
        GoDaikinCloudClient.parseCommandResponse(new JSONObject());check(true,"empty HTTP acknowledgement remains unverified");
        fail(Failure.COMMAND_REJECTED,()->GoDaikinCloudClient.parseCommandResponse(new JSONObject().put("success",false)));
        fail(Failure.COMMAND_REJECTED,()->GoDaikinCloudClient.parseCommandResponse(new JSONObject().put("statusCode",403)));
        fail(Failure.COMMAND_REJECTED,()->GoDaikinCloudClient.parseCommandResponse(new JSONObject().put("error","synthetic rejection")));
        protocol();return count;
    }
    private void protocol()throws Exception{
        CloudClientChecks.Mock wire=new CloudClientChecks.Mock();
        JSONObject auth=new JSONObject().put("AuthenticationResult",new JSONObject().put("IdToken","synthetic-id").put("AccessToken","synthetic-access").put("RefreshToken","synthetic-refresh").put("ExpiresIn",3600));
        JSONObject status=new JSONObject().put("thingName","mock-tv").put("key","synthetic-private-key").put("Set_Temp",23).put("Set_OnOff",1).put("Set_Mode",1).put("Set_Fan",128)
            .put("updatedOn","2026-10-28 17:00:00").put("shadowStateVersion",10).put("eventType","connected");
        JSONObject device=new JSONObject().put("ThingName","mock-tv").put("ACName","Mock TV").put("shadowState",status);
        wire.add(200,auth).add(200,new JSONObject().put("data",new JSONArray().put(device))).add(200,status).add(200,new JSONObject());
        TemperatureCommandGate gate=new TemperatureCommandGate();gate.bind("mock-tv");
        GoDaikinCloudClient client=new GoDaikinCloudClient((u,h,b)->{
            if(!CloudHttpsTransport.allowed(u,h,b)&&!gate.allow(u,h,b))throw new CloudException(Failure.CONFIRMATION_REQUIRED);return wire.post(u,h,b);
        },()->0);
        client.authenticate("synthetic@example.invalid","synthetic-only-password");client.readRegisteredDevices();
        GoDaikinCloudClient.DeviceStatus actual=client.readTargetState("mock-tv");check(actual.stateVersion==10,"state response version parsed");
        check(wire.requests.get(2).body.getJSONObject("requestData").getInt("type")==1,"status read does not include empty desired write");
        fail(Failure.CONFIRMATION_REQUIRED,()->client.submitTemperature("mock-tv",24));
        gate.permitOnce("mock-tv",24);client.submitTemperature("mock-tv",24);
        JSONObject d=wire.requests.get(3).body.getJSONObject("requestData");
        check(d.getInt("type")==3&&d.getString("thingName").equals("mock-tv"),"write serialization stable cloud ID not IP/name");
        JSONObject desired=d.getJSONObject("payload").getJSONObject("state").getJSONObject("desired");check(desired.length()==1&&desired.getInt("Set_Temp")==24,"temperature only command serialized");
        check(!client.safeReadShape().contains("mock-tv")&&!client.safeReadShape().contains("synthetic-private-key"),"protocol diagnostics redacted");
        fail(Failure.COMMAND_REJECTED,()->client.submitTemperature("mock-tv",32));
        client.close();
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS: "+new TemperatureChecks().run()+" synthetic control validation checks. No real cloud or AC commands.");}
}
