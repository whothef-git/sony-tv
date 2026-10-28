package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import java.util.function.LongSupplier;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.Failure;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.CloudException;

/** Phase 2.3 experiments only. Preparation is fresh and read-only; each transaction needs its own approval. */
final class ControlValidation {
    enum Kind { FAN, MODE, POWER }
    interface Device { TemperatureValidation.State read()throws Exception; void write(Kind kind,JSONObject desired)throws Exception; }
    private final String id;
    private final Device device;
    private final TemperatureValidation.Journal journal;
    private final LongSupplier mono,wall;
    private final TemperatureValidation.Pause pause;
    private final TemperatureValidation.Progress progress;
    private Plan prepared;
    static final class Plan {
        final Kind kind;final TemperatureValidation.State original;final JSONObject change,restore;final long created;boolean used;
        Plan(Kind k,TemperatureValidation.State s,JSONObject change,JSONObject restore,long time){kind=k;original=s;this.change=change;this.restore=restore;created=time;}
        String preview(){return "TV AC ONLY - "+kind+" TEST\nFresh original: "+summary(original)
            +"\nChange payload: "+change+"\nRestore payload: "+restore
            +"\nStable cloud ID checked privately. No other AC can be written."
            +"\nObservation hold after confirmation: "+hold(kind)/1000+" seconds, then restore and verify."
            +"\n"+(kind==Kind.POWER?"THIS SEPARATE TEST SENDS POWER OFF, THEN RESTORES ON.":"NO POWER ON/OFF FIELDS WILL BE SENT.")
            +"\nReview valid 15 minutes; fresh unchanged original state is checked again before any write.";}
    }
    static final class Outcome {
        final Kind kind;final boolean changed,restored;final String detail;
        Outcome(Kind k,boolean c,boolean r,String d){kind=k;changed=c;restored=r;detail=d;}
        String summary(){return "PHASE 2.3 REAL CLOUD "+kind+" TEST\nChange confirmed: "+changed+"\nOriginal settings restored and confirmed: "+restored
            +"\n"+detail+"\nFresh cloud state is verified; physical behavior requires the user's observation."
            +"\nNo other AC written. Production controls remain disabled.";}
    }
    ControlValidation(String id,Device device,TemperatureValidation.Journal journal,LongSupplier mono,LongSupplier wall,
        TemperatureValidation.Pause pause,TemperatureValidation.Progress progress){this.id=id;this.device=device;this.journal=journal;this.mono=mono;this.wall=wall;this.pause=pause;this.progress=progress;}
    static long hold(Kind kind){return kind==Kind.FAN?45000:180000;}
    static boolean blocked(JSONObject j){return TemperatureValidation.pending(j)&&!"PREPARED".equals(j.optString("phase"));}
    static String summary(TemperatureValidation.State s){return "power="+s.power+", mode="+mode(s.mode)+", fan="+fan(s.fan)+", target="+s.temp+" C, reported="+s.stamp+", version="+s.version;}
    static String mode(Integer n){return n==null?"UNKNOWN":n==1?"Cool":n==2?"Fan":n==4?"Dry":"Unsupported("+n+")";}
    static String fan(Integer n){return n==null?"UNKNOWN":n==2?"Low":n==4?"Medium":n==8?"High":n==128?"Auto":"Unsupported("+n+")";}
    private void metadata(TemperatureValidation.State s)throws CloudException{
        if(s==null||!id.equals(s.id))throw new CloudException(Failure.TARGET_MISMATCH);
        if(!"connected".equals(s.event))throw new CloudException(Failure.OFFLINE);
        long age=wall.getAsLong()-s.time();if(age< -5000||s.version==null||s.version<=0)throw new CloudException(Failure.STALE_STATE);
    }
    private void baseline(TemperatureValidation.State s)throws CloudException{
        metadata(s);
        if(s.power==null||s.power!=1||s.mode==null||(s.mode!=1&&s.mode!=2&&s.mode!=4)
            ||s.fan==null||(s.fan!=2&&s.fan!=4&&s.fan!=8&&s.fan!=128)||s.temp==null||s.temp<16||s.temp>31)
            throw new CloudException(Failure.STATE_CHANGED);
    }
    private boolean fresh(TemperatureValidation.State s)throws CloudException{metadata(s);return wall.getAsLong()-s.time()<=120000;}
    private static boolean allSame(TemperatureValidation.State a,TemperatureValidation.State b){
        return a.id.equals(b.id)&&java.util.Objects.equals(a.power,b.power)&&java.util.Objects.equals(a.mode,b.mode)
            &&java.util.Objects.equals(a.fan,b.fan)&&java.util.Objects.equals(a.temp,b.temp);
    }
    synchronized Plan prepare(Kind kind,boolean supportedSettingsVerified)throws Exception{
        if(!supportedSettingsVerified)throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        if(blocked(journal.load()))throw new CloudException(Failure.RESTORATION_REQUIRED);
        TemperatureValidation.State s=null;
        for(int i=0;i<40;i++){s=device.read();baseline(s);if(fresh(s))break;
            progress.show("READS ONLY - waiting for fresh state BEFORE "+kind+" preview ("+(i+1)+"/40). No command authorized.");pause.waitMs(15000);s=null;}
        if(s==null)throw new CloudException(Failure.STALE_STATE);
        JSONObject change=new JSONObject(),restore=new JSONObject();
        if(kind==Kind.FAN){change.put("Set_Fan",s.fan==8?2:8);restore.put("Set_Fan",s.fan);}
        else if(kind==Kind.MODE){change.put("Set_Mode",s.mode==2?1:2);restore.put("Set_Mode",s.mode).put("Set_Fan",s.fan).put("Set_Temp",s.temp);}
        else {change.put("Set_OnOff",0);restore.put("Set_OnOff",s.power).put("Set_Mode",s.mode).put("Set_Fan",s.fan).put("Set_Temp",s.temp);}
        prepared=new Plan(kind,s,change,restore,mono.getAsLong());
        journal.save(record(prepared,"PREPARED")); // Fresh original recorded BEFORE showing the approval dialog.
        return prepared;
    }
    private JSONObject record(Plan p,String phase)throws Exception{return new JSONObject().put("phase",phase).put("kind",p.kind.name())
        .put("original",p.original.json()).put("change",p.change).put("restore",p.restore);}
    synchronized Outcome run(Plan p,boolean explicitOnScreenApproval)throws Exception{
        if(!explicitOnScreenApproval||p==null||p!=prepared||p.used)throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        p.used=true;prepared=null;if(blocked(journal.load()))throw new CloudException(Failure.RESTORATION_REQUIRED);
        TemperatureValidation.State before=null;
        for(int i=0;i<40;i++){
            if(mono.getAsLong()-p.created>TemperatureValidation.PLAN_MS)throw new CloudException(Failure.PLAN_EXPIRED);
            before=device.read();baseline(before);if(!allSame(before,p.original))throw new CloudException(Failure.STATE_CHANGED);
            if(before.version<p.original.version||before.time()<p.original.time())throw new CloudException(Failure.STALE_STATE);
            if(fresh(before))break;progress.show("APPROVED "+p.kind+" - NO WRITE SENT. Waiting for fresh unchanged original state.");pause.waitMs(15000);before=null;
        }
        if(before==null)throw new CloudException(Failure.STALE_STATE);
        if(mono.getAsLong()-p.created>TemperatureValidation.PLAN_MS)throw new CloudException(Failure.PLAN_EXPIRED);
        JSONObject record=record(p,"CHANGE_ATTEMPT_PENDING").put("original",before.json());journal.save(record);
        boolean changed=false,restored=false;String detail="Recorded original: "+summary(before);TemperatureValidation.State after=null;
        try{
            progress.show("Sending ONE approved "+p.kind+" command. No blind retry.");
            try{device.write(p.kind,p.change);}catch(Exception e){detail+="\nCommand response uncertain/rejected; checking fresh state, without repeating the write.";}
            after=await(before,p.kind,p.change,false);changed=true;detail+="\nChanged: "+summary(after);
            record.put("phase","CHANGE_CONFIRMED");save(record);
            for(long remaining=hold(p.kind);remaining>0;remaining-=15000){
                progress.show("CHANGED STATE CONFIRMED - observe TV AC physically NOW. "+p.kind+" restoration in "+remaining/1000+" seconds.\n"+summary(after));
                pause.waitMs(Math.min(15000,remaining));
            }
        }catch(Exception e){detail+="\nChange/observation stage: "+TemperatureValidation.safeFailure(e);}
        finally{
            if(changed){record.put("phase","RESTORE_ATTEMPT_PENDING");save(record);
                try{
                    progress.show("Sending ONE approved restoration of "+p.kind+" original settings. No repeat write.");
                    try{device.write(p.kind,p.restore);}catch(Exception e){detail+="\nRestore acknowledgement uncertain/rejected; verifying without repeating.";}
                    TemperatureValidation.State original=await(after,p.kind,p.restore,true);restored=true;detail+="\nRestored: "+summary(original);record.put("phase","COMPLETE");
                }catch(Exception e){detail+="\nRestoration unverified: "+TemperatureValidation.safeFailure(e)+". Explicit recovery required.";}
            }
            record.put("changed",changed).put("restored",restored);save(record);
        }
        return new Outcome(p.kind,changed,restored,detail);
    }
    synchronized Outcome recover(boolean approved)throws Exception{
        if(!approved)throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        JSONObject j=journal.load();if(!blocked(j)||!j.has("kind"))throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        Kind kind=Kind.valueOf(j.getString("kind"));TemperatureValidation.State original=TemperatureValidation.State.from(j.getJSONObject("original"));
        if(!id.equals(original.id))throw new CloudException(Failure.TARGET_MISMATCH);
        TemperatureValidation.State current=device.read();if(!fresh(current))throw new CloudException(Failure.STALE_STATE);
        if(allSame(current,original)&&current.version>original.version&&current.time()>original.time()){
            j.put("phase","COMPLETE").put("restored",true);journal.save(j);return new Outcome(kind,j.optBoolean("changed"),true,"Original settings already present in fresh state. No recovery write sent.");
        }
        if(!matches(current,j.getJSONObject("change")))throw new CloudException(Failure.STATE_CHANGED);
        j.put("phase","RECOVERY_ATTEMPT_PENDING");journal.save(j);
        try{device.write(kind,j.getJSONObject("restore"));}catch(Exception ignored){}
        TemperatureValidation.State actual=await(current,kind,j.getJSONObject("restore"),true);j.put("phase","COMPLETE").put("restored",true);journal.save(j);
        return new Outcome(kind,j.optBoolean("changed"),true,"Explicit recovery confirmed: "+summary(actual));
    }
    private TemperatureValidation.State await(TemperatureValidation.State previous,Kind kind,JSONObject expected,boolean restoring)throws Exception{
        TemperatureValidation.State candidate=null;Exception last=new CloudException(Failure.STALE_STATE);
        for(int i=0;i<8;i++){
            pause.waitMs(7000);
            try{
                TemperatureValidation.State s=device.read();metadata(s);
                if(kind!=Kind.POWER&&!java.util.Objects.equals(s.power,previous.power))throw new CloudException(Failure.STATE_CHANGED);
                if(kind==Kind.FAN&&(!java.util.Objects.equals(s.mode,previous.mode)||!java.util.Objects.equals(s.temp,previous.temp)))throw new CloudException(Failure.STATE_CHANGED);
                if(fresh(s)&&matches(s,expected)&&s.version>previous.version&&s.time()>previous.time()){
                    if(candidate!=null&&s.version>=candidate.version&&s.time()>=candidate.time())return s;candidate=s;
                }else candidate=null;
            }catch(CloudException e){candidate=null;last=e;if(e.reason==Failure.OFFLINE||e.reason==Failure.TARGET_MISMATCH||e.reason==Failure.STATE_CHANGED)throw e;}
            catch(Exception e){candidate=null;last=e;}
            progress.show("Waiting for fresh "+(restoring?"restored":"changed")+" "+kind+" state ("+(i+1)+"/8). Never retrying write.");
        }throw last;
    }
    static boolean matches(TemperatureValidation.State s,JSONObject expected){
        return (!expected.has("Set_OnOff")||java.util.Objects.equals(s.power,expected.opt("Set_OnOff")))
            &&(!expected.has("Set_Mode")||java.util.Objects.equals(s.mode,expected.opt("Set_Mode")))
            &&(!expected.has("Set_Fan")||java.util.Objects.equals(s.fan,expected.opt("Set_Fan")))
            &&(!expected.has("Set_Temp")||java.util.Objects.equals(s.temp,expected.opt("Set_Temp")));
    }
    private void save(JSONObject record){try{journal.save(record);}catch(Exception e){progress.show("Journal update failed; original is preserved. Check restoration before continuing.");}}
}
