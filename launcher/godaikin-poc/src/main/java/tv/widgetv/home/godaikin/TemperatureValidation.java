package tv.widgetv.home.godaikin;

import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.Failure;
import static tv.widgetv.home.godaikin.GoDaikinCloudClient.CloudException;

/** Serialized, bounded two-command transaction. Construction/prepare never writes to an AC. */
final class TemperatureValidation {
    static final long MAX_AGE_MS=120000,PLAN_MS=15*60*1000;
    static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    static final ZoneId CLOUD_ZONE=ZoneId.of("Asia/Kuala_Lumpur");
    interface Device { State read()throws Exception; void temperature(int value)throws Exception; }
    interface Journal { JSONObject load()throws Exception; void save(JSONObject value)throws Exception; }
    interface Pause { void waitMs(long millis)throws InterruptedException; }
    interface Progress { void show(String text); }

    static final class State {
        final String id,event,stamp;
        final Integer power,mode,fan,temp;
        final Long version;
        State(GoDaikinCloudClient.DeviceStatus d){
            id=d.id;event=d.event;stamp=d.updatedOn;power=d.power;mode=d.mode;fan=d.fan;version=d.stateVersion;
            temp=d.targetTemperature!=null&&d.targetTemperature==Math.rint(d.targetTemperature)?d.targetTemperature.intValue():null;
        }
        State(String id,String event,String stamp,Integer power,Integer mode,Integer fan,Integer temp,Long version){
            this.id=id;this.event=event;this.stamp=stamp;this.power=power;this.mode=mode;this.fan=fan;this.temp=temp;this.version=version;
        }
        long time()throws CloudException{try{return LocalDateTime.parse(stamp,STAMP).atZone(CLOUD_ZONE).toInstant().toEpochMilli();}
            catch(Exception e){throw new CloudException(Failure.STALE_STATE);}}
        JSONObject json()throws Exception{return new JSONObject().put("id",id).put("event",event).put("stamp",stamp)
            .put("power",power).put("mode",mode).put("fan",fan).put("temp",temp).put("version",version);}
        static State from(JSONObject j)throws Exception{return new State(j.getString("id"),j.getString("event"),j.getString("stamp"),
            j.getInt("power"),j.getInt("mode"),j.getInt("fan"),j.getInt("temp"),j.getLong("version"));}
        String safeSummary(){return "TV AC: target="+temp+" C, power="+(Integer.valueOf(1).equals(power)?"ON":"UNKNOWN/OFF")
            +", cloud event="+event+", reported="+stamp+", state version="+version;}
    }
    static final class Plan {
        final State original;
        final int changed;
        final long created;
        private boolean used;
        private Plan(State s,long time){original=s;changed=s.temp<31?s.temp+1:s.temp-1;created=time;}
        String preview(){return original.safeSummary()+"\n\nTemperature-only payload: {state:{desired:{Set_Temp:"+changed+"}}}"
            +"\nAfter fresh confirmation, restore Set_Temp:"+original.temp+" and verify it."
            +"\nDestination: registered TV AC cloud ID (privately verified; identifier/key redacted)."
            +"\nThis preparation may use a cached snapshot. Before any write the device must freshly report this SAME original target."
            +"\nAfter confirmation, allow up to 10 minutes for fresh state. No power, mode, fan or other AC commands. Review expires in 15 minutes.";}
    }
    static final class Outcome {
        final boolean changed,restored;
        final String details;
        Outcome(boolean changed,boolean restored,String details){this.changed=changed;this.restored=restored;this.details=details;}
        String summary(){return "PHASE 2.2 REAL CLOUD VALIDATION\nChange confirmed: "+changed+"\nOriginal temperature restored and confirmed: "+restored
            +"\n"+details+"\nConfirmation requires fresh subsequent cloud-reported state, never HTTP success alone."
            +"\nNo power commands. Other ACs remained read-only. Production controls remain disabled.";}
    }

    private final String targetId;
    private final Device device;
    private final Journal journal;
    private final LongSupplier monotonic,wall;
    private final Pause pause;
    private final Progress progress;
    private Plan prepared;
    private boolean running;
    TemperatureValidation(String targetId,Device device,Journal journal,LongSupplier monotonic,LongSupplier wall,Pause pause,Progress progress){
        this.targetId=targetId;this.device=device;this.journal=journal;this.monotonic=monotonic;this.wall=wall;this.pause=pause;this.progress=progress;
    }
    private void known(State state)throws CloudException{
        if(state==null||!targetId.equals(state.id))throw new CloudException(Failure.TARGET_MISMATCH);
        if(!"connected".equals(state.event))throw new CloudException(Failure.OFFLINE);
        long age=wall.getAsLong()-state.time();
        if(age< -5000||state.version==null||state.version<=0)throw new CloudException(Failure.STALE_STATE);
        if(!Integer.valueOf(1).equals(state.power)||!Integer.valueOf(1).equals(state.mode)||state.fan==null
            ||state.temp==null||state.temp<16||state.temp>31)throw new CloudException(Failure.STATE_CHANGED);
    }
    void fresh(State state)throws CloudException{known(state);if(wall.getAsLong()-state.time()>MAX_AGE_MS)throw new CloudException(Failure.STALE_STATE);}
    static boolean sameSettings(State a,State b){return a.id.equals(b.id)&&a.power.equals(b.power)&&a.mode.equals(b.mode)&&a.fan.equals(b.fan);}
    static boolean pending(JSONObject value){return value!=null&&!"COMPLETE".equals(value.optString("phase"));}
    synchronized Plan prepare()throws Exception{
        if(running)throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        if(pending(journal.load()))throw new CloudException(Failure.RESTORATION_REQUIRED);
        State original=device.read();known(original);prepared=new Plan(original,monotonic.getAsLong());return prepared;
    }
    synchronized Outcome run(Plan plan,boolean onScreenApproved)throws Exception{
        if(!onScreenApproved||plan==null||plan!=prepared||plan.used||running)throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        plan.used=true;prepared=null;
        if(monotonic.getAsLong()-plan.created>PLAN_MS)throw new CloudException(Failure.PLAN_EXPIRED);
        if(pending(journal.load()))throw new CloudException(Failure.RESTORATION_REQUIRED);
        State before=awaitFreshOriginal(plan);
        if(monotonic.getAsLong()-plan.created>PLAN_MS)throw new CloudException(Failure.PLAN_EXPIRED);
        if(!sameSettings(before,plan.original)||!before.temp.equals(plan.original.temp)
            ||before.version<plan.original.version||before.time()<plan.original.time())throw new CloudException(Failure.STATE_CHANGED);
        JSONObject record=new JSONObject().put("phase","CHANGE_ATTEMPT_PENDING").put("original",before.json()).put("changed",plan.changed);
        journal.save(record); // Durable encrypted original required BEFORE any network write.
        running=true;boolean changed=false,restored=false;String details="Original recorded: "+before.temp+" C; test target: "+plan.changed+" C.";
        State after=null;
        try{
            progress.show("Original recorded. Sending ONE temperature adjustment; no automatic retries.");
            try{device.temperature(plan.changed);}catch(Exception e){details+=" Adjustment response uncertain/rejected; checking fresh state without repeating command.";}
            after=await(before,plan.changed);
            changed=true;record.put("phase","CHANGE_CONFIRMED");saveBestEffort(record);
            details+="\nChange evidence: "+after.safeSummary();
        }catch(Exception e){details+="\nAdjustment not confirmed: "+safeFailure(e)+". A restoration check is required; no blind retry.";}
        finally{
            if(changed){
                progress.show("Fresh changed state confirmed. Restoring the original temperature with ONE command.");
                record.put("phase","RESTORE_ATTEMPT_PENDING");saveBestEffort(record);
                try{
                    try{device.temperature(before.temp);}catch(Exception e){details+="\nRestore response uncertain/rejected; reading state without repeating command.";}
                    State actual=await(after,before.temp);restored=true;details+="\nRestoration evidence: "+actual.safeSummary();
                    record.put("phase","COMPLETE");
                }catch(Exception e){details+="\nRestoration not confirmed: "+safeFailure(e)+". Explicit recovery approval is required.";}
            }
            record.put("changeConfirmed",changed).put("restored",restored);saveBestEffort(record);running=false;
        }
        return new Outcome(changed,restored,details);
    }
    private State awaitFreshOriginal(Plan plan)throws Exception{
        // The user approves a precise temperature pair, not permission to bypass stale state.
        // Polling here happens only inside that explicitly confirmed transaction, never on startup.
        for(int i=0;i<40;i++){
            if(monotonic.getAsLong()-plan.created>PLAN_MS)throw new CloudException(Failure.PLAN_EXPIRED);
            State current=device.read();known(current);
            if(!sameSettings(current,plan.original)||!current.temp.equals(plan.original.temp)
                ||current.version<plan.original.version||current.time()<plan.original.time())throw new CloudException(Failure.STATE_CHANGED);
            if(wall.getAsLong()-current.time()<=MAX_AGE_MS)return current;
            progress.show("APPROVED - NO WRITE SENT. Waiting for fresh TV AC state ("+(i+1)+"/40). Original target must still be "+plan.original.temp+" C.");
            pause.waitMs(15000);
        }
        throw new CloudException(Failure.STALE_STATE);
    }
    /** Explicit recovery only, never invoked by startup, preparation, timer or instrumentation. */
    synchronized Outcome recover(boolean onScreenApproved)throws Exception{
        if(!onScreenApproved||running)throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        JSONObject record=journal.load();if(!pending(record))throw new CloudException(Failure.CONFIRMATION_REQUIRED);
        State original=State.from(record.getJSONObject("original"));if(!targetId.equals(original.id))throw new CloudException(Failure.TARGET_MISMATCH);
        State current=device.read();fresh(current);
        if(!sameSettings(original,current))throw new CloudException(Failure.STATE_CHANGED);
        if(current.temp.equals(original.temp)&&current.version>original.version&&current.time()>original.time()){
            record.put("phase","COMPLETE").put("restored",true);journal.save(record);
            return new Outcome(record.optBoolean("changeConfirmed"),true,"Fresh original temperature already present. No recovery write sent.\n"+current.safeSummary());
        }
        if(current.temp!=record.getInt("changed"))throw new CloudException(Failure.STATE_CHANGED);
        record.put("phase","RECOVERY_ATTEMPT_PENDING");journal.save(record);running=true;
        try{
            try{device.temperature(original.temp);}catch(Exception ignored){}
            State restored=await(current,original.temp);record.put("phase","COMPLETE").put("restored",true);journal.save(record);
            return new Outcome(record.optBoolean("changeConfirmed"),true,"Explicit single recovery command confirmed.\n"+restored.safeSummary());
        }finally{running=false;}
    }
    private State await(State previous,int expected)throws Exception{
        Exception last=new CloudException(Failure.STALE_STATE);
        // At most eight read attempts. Reads can retry; writes cannot. Two consistent fresh observations.
        State candidate=null;
        for(int i=0;i<8;i++){
            pause.waitMs(7000);
            try{
                State current=device.read();fresh(current);
                if(!sameSettings(previous,current))throw new CloudException(Failure.STATE_CHANGED);
                if(current.temp==expected&&current.version>previous.version&&current.time()>previous.time()){
                    if(candidate!=null&&current.version>=candidate.version&&current.time()>=candidate.time())return current;
                    candidate=current;
                }else candidate=null;
            }catch(CloudException e){candidate=null;last=e;if(e.reason==Failure.TARGET_MISMATCH||e.reason==Failure.STATE_CHANGED||e.reason==Failure.OFFLINE)throw e;}
            catch(Exception e){candidate=null;last=e;}
            progress.show("Waiting for fresh reported state (read "+(i+1)+"/8). No repeat write.");
        }
        throw last;
    }
    private void saveBestEffort(JSONObject record){try{journal.save(record);}catch(Exception e){progress.show("Journal update failed; the original record remains saved. Verify restoration before closing.");}}
    static String safeFailure(Exception e){return e instanceof CloudException?((CloudException)e).reason.name():"LOCAL_OR_NETWORK_FAILURE";}
}
