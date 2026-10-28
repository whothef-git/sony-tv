package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.Failure;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.CloudException;

/** Synthetic fan/mode/power tests. Never communicates with real cloud or ACs. */
public final class ControlChecks {
    int checks;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    interface Action { void run()throws Exception; }
    void fail(Failure f,Action action)throws Exception{try{action.run();throw new AssertionError("expected "+f);}catch(CloudException e){check(e.reason==f,"expected "+f);}}
    static class Rig implements ControlValidation.Device,TemperatureValidation.Journal {
        AtomicLong wall=new AtomicLong(Instant.parse("2026-10-28T10:00:00Z").toEpochMilli()),mono=new AtomicLong();
        String id="mock-tv",event="connected";int power=1,mode=1,fan=2,temp=23;long version=100,stamp=wall.get();
        JSONObject pending,journal;boolean stale,reject,timeout,failRestore,storageFail,modeSideEffects;
        final List<JSONObject> writes=new ArrayList<>();
        void tick(long value){wall.addAndGet(value);mono.addAndGet(value);}
        public TemperatureValidation.State read(){
            if(pending!=null){
                if(pending.has("Set_OnOff"))power=pending.optInt("Set_OnOff");if(pending.has("Set_Mode"))mode=pending.optInt("Set_Mode");
                if(pending.has("Set_Fan"))fan=pending.optInt("Set_Fan");if(pending.has("Set_Temp"))temp=pending.optInt("Set_Temp");
                if(modeSideEffects&&pending.length()==1&&pending.has("Set_Mode")&&mode==2){fan=128;temp=0;}
                pending=null;if(!stale){version++;stamp=wall.get();}
            }
            String time=TemperatureValidation.STAMP.format(Instant.ofEpochMilli(stamp).atZone(TemperatureValidation.CLOUD_ZONE));
            return new TemperatureValidation.State(id,event,time,power,mode,fan,temp,version);
        }
        public void write(ControlValidation.Kind kind,JSONObject value)throws Exception{
            writes.add(new JSONObject(value.toString()));if(reject||failRestore&&writes.size()==2)throw new CloudException(Failure.COMMAND_REJECTED);
            pending=new JSONObject(value.toString());if(timeout)throw new java.net.SocketTimeoutException("synthetic");
        }
        public JSONObject load(){return journal;}
        public void save(JSONObject value)throws Exception{if(storageFail)throw new java.io.IOException();journal=new JSONObject(value.toString());}
        ControlValidation engine(){return new ControlValidation("mock-tv",this,this,mono::get,wall::get,this::tick,s->{});}
    }
    public int run()throws Exception{
        for(ControlValidation.Kind kind:ControlValidation.Kind.values()){
            Rig r=new Rig();ControlValidation engine=r.engine();
            fail(Failure.CONFIRMATION_REQUIRED,()->engine.prepare(kind,false));check(r.writes.isEmpty(),"capability verification required before preview");
            ControlValidation.Plan plan=engine.prepare(kind,true);
            check(r.writes.isEmpty()&&r.journal.getString("phase").equals("PREPARED"),"fresh original recorded without write");
            check(r.journal.getJSONObject("original").getInt("fan")==2,"original fan persisted");
            check(!plan.preview().contains("mock-tv"),"preview does not disclose actual ID");
            fail(Failure.CONFIRMATION_REQUIRED,()->engine.run(plan,false));check(r.writes.isEmpty(),"no write without screen approval");
            ControlValidation.Outcome outcome=engine.run(plan,true);
            check(outcome.changed&&outcome.restored,"change and restoration "+kind);
            check(r.writes.size()==2&&r.power==1&&r.mode==1&&r.fan==2&&r.temp==23,"exactly two writes restore all tracked originals");
            check(r.mono.get()==ControlValidation.hold(kind)+28000,"two fresh observations per stage and physical-observation hold");
            check("COMPLETE".equals(r.journal.getString("phase")),"completion persisted");
            if(kind!=ControlValidation.Kind.POWER)check(r.writes.stream().noneMatch(j->j.has("Set_OnOff")),"no power fields in fan/mode test");
            else check(r.writes.get(0).getInt("Set_OnOff")==0&&r.writes.get(1).getInt("Set_OnOff")==1,"power only in separate explicit test");
            fail(Failure.CONFIRMATION_REQUIRED,()->engine.run(plan,true));check(r.writes.size()==2,"used approval cannot resend");
        }
        Rig side=new Rig();side.modeSideEffects=true;ControlValidation se=side.engine();ControlValidation.Outcome so=se.run(se.prepare(ControlValidation.Kind.MODE,true),true);
        check(so.changed&&so.restored&&side.fan==2&&side.temp==23,"mode-dependent fan/temperature restored explicitly without power");
        Rig old=new Rig();old.stamp-=121000;fail(Failure.STALE_STATE,()->old.engine().prepare(ControlValidation.Kind.FAN,true));check(old.writes.isEmpty()&&old.journal==null,"stale snapshots never reach preview/confirmation");
        Rig expired=new Rig();ControlValidation ee=expired.engine();ControlValidation.Plan ep=ee.prepare(ControlValidation.Kind.FAN,true);expired.tick(900001);
        fail(Failure.PLAN_EXPIRED,()->ee.run(ep,true));check(expired.writes.isEmpty(),"expired approval no write");
        Rig changed=new Rig();ControlValidation ce=changed.engine();ControlValidation.Plan cp=ce.prepare(ControlValidation.Kind.MODE,true);changed.temp=24;
        fail(Failure.STATE_CHANGED,()->ce.run(cp,true));check(changed.writes.isEmpty(),"changed original requires new preview");
        Rig off=new Rig();off.power=0;fail(Failure.STATE_CHANGED,()->off.engine().prepare(ControlValidation.Kind.POWER,true));check(off.writes.isEmpty(),"no automatic power-on baseline");
        Rig offline=new Rig();offline.event="disconnected";fail(Failure.OFFLINE,()->offline.engine().prepare(ControlValidation.Kind.FAN,true));
        Rig wrong=new Rig();wrong.id="mock-other";fail(Failure.TARGET_MISMATCH,()->wrong.engine().prepare(ControlValidation.Kind.MODE,true));
        Rig unsupported=new Rig();unsupported.mode=3;fail(Failure.STATE_CHANGED,()->unsupported.engine().prepare(ControlValidation.Kind.MODE,true));
        Rig clock=new Rig();clock.stamp+=6000;fail(Failure.STALE_STATE,()->clock.engine().prepare(ControlValidation.Kind.FAN,true));
        Rig failStore=new Rig();failStore.storageFail=true;try{failStore.engine().prepare(ControlValidation.Kind.FAN,true);throw new AssertionError("save failure");}catch(java.io.IOException expected){check(failStore.writes.isEmpty(),"original storage failure prevents write");}
        Rig reject=new Rig();reject.reject=true;ControlValidation re=reject.engine();ControlValidation.Outcome ro=re.run(re.prepare(ControlValidation.Kind.FAN,true),true);
        check(!ro.changed&&!ro.restored&&reject.writes.size()==1&&ControlValidation.blocked(reject.journal),"rejection unverified no retry pending journal");
        Rig ambiguous=new Rig();ambiguous.timeout=true;ControlValidation ae=ambiguous.engine();ControlValidation.Outcome ao=ae.run(ae.prepare(ControlValidation.Kind.MODE,true),true);
        check(ao.changed&&ao.restored&&ambiguous.writes.size()==2,"ambiguous acknowledgement reconciled with fresh state no repeat write");
        Rig stale=new Rig();ControlValidation st=stale.engine();ControlValidation.Plan sp=st.prepare(ControlValidation.Kind.FAN,true);stale.stale=true;
        ControlValidation.Outcome sto=st.run(sp,true);check(!sto.changed&&!sto.restored&&stale.writes.size()==1,"stale desired echo is never command success");
        Rig restoreFail=new Rig();restoreFail.failRestore=true;ControlValidation rf=restoreFail.engine();ControlValidation.Outcome rfo=rf.run(rf.prepare(ControlValidation.Kind.FAN,true),true);
        check(rfo.changed&&!rfo.restored&&restoreFail.writes.size()==2,"restore rejected no blind retry");
        fail(Failure.RESTORATION_REQUIRED,()->restoreFail.engine().prepare(ControlValidation.Kind.MODE,true));
        fail(Failure.CONFIRMATION_REQUIRED,()->restoreFail.engine().recover(false));restoreFail.failRestore=false;
        check(restoreFail.engine().recover(true).restored&&restoreFail.writes.size()==3,"explicit recovery only restores recorded field");
        gate();return checks;
    }
    private void gate()throws Exception{
        ControlCommandGate gate=new ControlCommandGate("mock-tv");Map<String,String> headers=new HashMap<>();headers.put("authorization","synthetic-token");
        JSONObject fan=new JSONObject().put("Set_Fan",8),mode=new JSONObject().put("Set_Mode",2),power=new JSONObject().put("Set_OnOff",0);
        check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,request("mock-tv",fan)),"initial gate is not armed");
        fail(Failure.TARGET_MISMATCH,()->gate.arm("mock-other",ControlValidation.Kind.FAN,fan));
        fail(Failure.TARGET_MISMATCH,()->gate.arm("mock-tv",ControlValidation.Kind.MODE,new JSONObject().put("Set_Mode",2).put("Set_OnOff",1)));
        fail(Failure.COMMAND_REJECTED,()->gate.arm("mock-tv",ControlValidation.Kind.FAN,mode));
        fail(Failure.COMMAND_REJECTED,()->gate.arm("mock-tv",ControlValidation.Kind.POWER,fan));
        for(ControlValidation.Kind kind:ControlValidation.Kind.values()){
            JSONObject payload=kind==ControlValidation.Kind.FAN?fan:kind==ControlValidation.Kind.MODE?mode:power;
            gate.arm("mock-tv",kind,payload);
            check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,request("mock-other",payload)),"cross-target denied");
            JSONObject extra=new JSONObject(payload.toString()).put("Set_LEDOff",1);check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,request("mock-tv",extra)),"unreviewed field denied");
            check(gate.allow(GoDaikinCloudClient.STATE_URL,headers,request("mock-tv",payload)),"exact approved payload allowed");
            check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,request("mock-tv",payload)),"permit consumed before network");
        }
        check(!ControlCommandGate.validFields(new JSONObject().put("Set_Mode",3)),"no assumed auto/heat modes");
        check(!ControlCommandGate.validFields(new JSONObject().put("Set_Fan",16)),"Dining five-speed protocol outside TV AC scope");
        check(!ControlCommandGate.validFields(new JSONObject().put("Set_Fan",8.0)),"noninteger control value rejected");
        gate.arm("mock-tv",ControlValidation.Kind.FAN,fan);gate.revoke();check(!gate.allow(GoDaikinCloudClient.STATE_URL,headers,request("mock-tv",fan)),"revoked permit cannot send");
    }
    static JSONObject request(String id,JSONObject desired)throws Exception{return new JSONObject().put("requestData",new JSONObject().put("type",3).put("username","synthetic@example.invalid")
        .put("thingName",id).put("key","synthetic-key").put("payload",new JSONObject().put("state",new JSONObject().put("desired",desired))));}
    public static void main(String[] args)throws Exception{System.out.println("PASS: "+new ControlChecks().run()+" synthetic Phase 2.3 checks. No real network or AC commands.");}
}
