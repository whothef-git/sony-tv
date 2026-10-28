package tv.widgetv.home.godaikin;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate developer tests. No startup commands, intent approvals, or unpinned identity fallback. */
public final class ControlTestActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Button> buttons=new ArrayList<>();
    private CloudCredentialVault vault;
    private ControlCommandGate gate;
    private GoDaikinCloudClient cloud;
    private String targetId;
    private ControlValidation engine;
    private TextView output;
    private Button recover;
    private volatile boolean busy,destroyed;
    private volatile ControlValidation.Kind active;
    interface Job{void run()throws Exception;}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);vault=new CloudCredentialVault(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(60,24,60,24);
        root.setBackgroundColor(0xff081728);scroll.addView(root);setContentView(scroll);
        TextView title=new TextView(this);title.setTextSize(25);title.setText("Phase 2.3 - TV AC fan / mode / separate power tests");root.addView(title);
        TextView info=new TextView(this);info.setTextSize(16);info.setText("TV AC stable cloud ID pinned from Phase 2.2; other ACs are read-only.\nUser verified in GO DAIKIN: Cool / Fan / Dry; three manual fan speeds.\nPrepare reads and records FRESH state, then shows a separate confirmation. No writes before confirmation.\nObserve airflow/indicator during the hold. Fan hold: 45 s; mode/power hold: 3 min.\nPower OFF/ON is restricted to its separately approved POWER test. Production controls stay disabled.");root.addView(info);
        Button read=button(root,"Read TV AC state and capability flags");read.setOnClickListener(v->job(()->{ensure();GoDaikinCloudClient.DeviceStatus d=readStatus();show("READ ONLY\n"+ControlValidation.summary(new TemperatureValidation.State(d))
            +"\nReported capability flags (semantics not assumed): "+d.capabilityFlags+"\nIndoor RPM="+d.indoorRpm+", compressor report="+d.compressorOn
            +"\nSelectable settings verified by user in official app; no mode beyond Cool/Fan/Dry enabled.");}));
        button(root,"Prepare FAN test - fresh read, then review").setOnClickListener(v->prepare(ControlValidation.Kind.FAN));
        button(root,"Prepare MODE test - fresh read, then review").setOnClickListener(v->prepare(ControlValidation.Kind.MODE));
        button(root,"Prepare separate POWER test - no automatic ON/OFF").setOnClickListener(v->prepare(ControlValidation.Kind.POWER));
        recover=button(root,"Review pending original-setting recovery");recover.setVisibility(View.GONE);recover.setOnClickListener(v->job(()->{
            ensure();JSONObject record=vault.loadControl("journal");if(!ControlValidation.blocked(record)||!record.has("kind"))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.RESTORATION_REQUIRED);
            ControlValidation.Kind kind=ControlValidation.Kind.valueOf(record.getString("kind"));active=kind;
            String message="Recover ONLY the recorded TV AC "+kind+" settings: "+record.getJSONObject("restore")
                +". Identity/current state are read first. "+(kind==ControlValidation.Kind.POWER?"This recovery includes the originally approved power state.":"No power fields.");
            runOnUiThread(()->confirm("Separate recovery approval",message,"Confirm recovery",()->finishTest(engine.recover(true))));
        }));
        output=new TextView(this);output.setTextSize(16);output.setText("No test prepared or approved. Startup sends no requests.");root.addView(output);read.requestFocus();
    }
    private Button button(LinearLayout root,String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);root.addView(b);buttons.add(b);return b;}
    private void prepare(ControlValidation.Kind kind){job(()->{
        ensure();active=kind;show("PREPARING "+kind+" - READS ONLY. Waiting for fresh state BEFORE recording original and showing confirmation.");
        ControlValidation.Plan plan=engine.prepare(kind,true);show("PREPARED - NO WRITE SENT\n"+plan.preview());
        runOnUiThread(()->confirm(kind==ControlValidation.Kind.POWER?"Separate POWER OFF / restore ON approval":"Confirm TV AC "+kind+" test",
            plan.preview()+"\nKeep this screen open and observe the AC. Do not use the phone app/remote to change settings during this test.",
            kind==ControlValidation.Kind.POWER?"Confirm POWER OFF and restore ON":"Confirm "+kind+" test and restore",()->finishTest(engine.run(plan,true))));
    });}
    private void confirm(String title,String text,String positive,Job action){
        if(destroyed)return;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(text).setNegativeButton("Cancel - no command",(d,w)->{})
            .setPositiveButton(positive,(d,w)->job(action)).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());dialog.show();
    }
    private void finishTest(ControlValidation.Outcome outcome){
        active=outcome.kind;String message=outcome.summary();writeFile("phase23-"+outcome.kind.name().toLowerCase(java.util.Locale.ROOT)+"-result.txt",message);show(message);
    }
    private void ensure()throws Exception{
        if(engine!=null)return;
        JSONObject binding=vault.loadControl("binding");if(binding==null)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);
        targetId=binding.getString("id"); // No display-name/IP fallback in Phase 2.3.
        CloudCredentialVault.Account account=vault.load();if(account==null)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED);
        gate=new ControlCommandGate(targetId);cloud=new GoDaikinCloudClient(new CloudHttpsTransport(gate),SystemClock::elapsedRealtime);
        try{
            cloud.authenticate(account.user,account.password);readStatus();
            engine=new ControlValidation(targetId,new ControlValidation.Device(){
                public TemperatureValidation.State read()throws Exception{return new TemperatureValidation.State(readStatus());}
                public void write(ControlValidation.Kind kind,JSONObject value)throws Exception{
                    gate.arm(targetId,kind,value);try{cloud.submitDesired(targetId,value);}finally{gate.revoke();}
                }
            },new TemperatureValidation.Journal(){
                public JSONObject load()throws Exception{return vault.loadControl("journal");}
                public void save(JSONObject value)throws Exception{vault.saveControl("journal",value);}
            },SystemClock::elapsedRealtime,System::currentTimeMillis,Thread::sleep,this::show);
        }catch(Exception e){cloud.close();cloud=null;engine=null;throw e;}
    }
    private GoDaikinCloudClient.DeviceStatus readStatus()throws Exception{
        GoDaikinCloudClient.DeviceStatus listed=null;
        for(GoDaikinCloudClient.DeviceStatus d:cloud.readRegisteredDevices().devices)if(targetId.equals(d.id)){listed=d;break;}
        if(listed==null)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);
        GoDaikinCloudClient.DeviceStatus actual=cloud.readTargetState(targetId);
        if(!java.util.Objects.equals(actual.power,listed.power)||!java.util.Objects.equals(actual.mode,listed.mode)
            ||!java.util.Objects.equals(actual.fan,listed.fan)||!java.util.Objects.equals(actual.targetTemperature,listed.targetTemperature))
            throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.STALE_STATE);
        return actual;
    }
    private void job(Job action){
        if(busy||destroyed)return;busy=true;for(Button b:buttons)b.setEnabled(false);
        worker.execute(()->{try{action.run();}catch(Exception e){show("PHASE 2.3 STOPPED: "+TemperatureValidation.safeFailure(e)+". No blind retry. Original-setting journal retained.");
            if(e instanceof GoDaikinCloudClient.CloudException){GoDaikinCloudClient.Failure reason=((GoDaikinCloudClient.CloudException)e).reason;
                if(reason==GoDaikinCloudClient.Failure.AUTH_REJECTED||reason==GoDaikinCloudClient.Failure.API_REJECTED||reason==GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED){if(cloud!=null)cloud.close();cloud=null;engine=null;}}
        }finally{
            boolean pending;try{pending=ControlValidation.blocked(vault.loadControl("journal"));}catch(Exception e){pending=true;}
            final boolean showRecovery=pending;busy=false;runOnUiThread(()->{if(!destroyed){for(Button b:buttons)b.setEnabled(true);recover.setVisibility(showRecovery?View.VISIBLE:View.GONE);}});
        }});
    }
    private void writeFile(String name,String text){try(java.io.FileOutputStream out=openFileOutput(name,MODE_PRIVATE)){out.write(text.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
    private void show(String text){writeFile("control-validation-result.txt",text);runOnUiThread(()->{if(!destroyed&&output!=null)output.setText(text);});}
    @Override public void onBackPressed(){if(busy){new AlertDialog.Builder(this).setTitle("Test in progress").setMessage("Keep the test open until original settings are restored and verified.").setPositiveButton("Stay",null).show();return;}super.onBackPressed();}
    @Override protected void onDestroy(){destroyed=true;worker.execute(()->{if(cloud!=null)cloud.close();});worker.shutdown();super.onDestroy();}
}
