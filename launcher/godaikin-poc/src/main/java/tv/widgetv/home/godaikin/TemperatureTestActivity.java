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

/** Developer-only isolated screen. No intent/broadcast/timer can approve or submit a command. */
public final class TemperatureTestActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Button> buttons=new ArrayList<>();
    private CloudCredentialVault vault;
    private TemperatureCommandGate gate;
    private GoDaikinCloudClient cloud;
    private GoDaikinCloudClient.DeviceStatus target;
    private TemperatureValidation validation;
    private TemperatureValidation.Plan plan;
    private TextView output;
    private Button review,recovery;
    private volatile boolean destroyed,busy;
    private volatile String lastText="Not tested. No cloud requests or commands sent.";
    private String listEvidence="";

    interface Job{void run()throws Exception;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        vault=new CloudCredentialVault(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60,24,60,24);root.setBackgroundColor(0xff081728);scroll.addView(root);setContentView(scroll);
        TextView title=new TextView(this);title.setTextSize(25);title.setText("Phase 2.2 - TV AC temperature validation only");root.addView(title);
        TextView intro=new TextView(this);intro.setTextSize(16);intro.setText("FTKH35BV1MF | firmware V1.2.5 | registered TV AC only\nStable cloud ID is privately checked; never shown or logged.\nRead, prepare and review do NOT send AC commands. First write requires a separate confirmation.\nApproved test: change by 1 C, verify fresh state, restore original and verify again. No power commands.\nCloud timestamp interpretation: Malaysia time (Asia/Kuala_Lumpur); stale/future reports block the test.");root.addView(intro);
        Button read=button(root,"Read current TV AC state");read.setOnClickListener(v->job(()->{
            ensureSession();TemperatureValidation.State actual=readState();
            show(actual.safeSummary()+"\n"+listEvidence+"\n"+cloud.safeReadShape()+"\nNo AC write commands sent by Read.\n"+readiness(actual));
        }));
        Button prepare=button(root,"Prepare 1 C temperature adjustment");prepare.setOnClickListener(v->job(()->{
            ensureSession();plan=validation.prepare();show("PREPARED - NO WRITE SENT\n"+plan.preview());
        }));
        review=button(root,"Review command payload (no write)");review.setOnClickListener(v->{
            if(plan==null){show("Prepare a fresh plan first. No command sent.");return;}
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Confirm one TV AC test and restoration")
                .setMessage(plan.preview()+"\n\nSelect Cancel to send nothing. Confirm authorizes waiting for fresh unchanged original state, this single adjustment and its single restoration. Keep this screen open; do not change the AC in GO DAIKIN during this test.")
                .setNegativeButton("Cancel - send nothing",(d,w)->{})
                .setPositiveButton("Confirm test and restore",(d,w)->job(()->{
                    ensureSession();
                    vault.saveControl("binding",new JSONObject().put("id",target.id).put("device","TV AC").put("verifiedFirmware",target.firmware));
                    TemperatureValidation.Outcome outcome=validation.run(plan,true);plan=null;show(outcome.summary());
                })).create();
            dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());dialog.show();
        });
        recovery=button(root,"Review recovery of original temperature");recovery.setOnClickListener(v->job(()->{
            ensureSession();JSONObject record=vault.loadControl("journal");
            if(!TemperatureValidation.pending(record)){show("No pending restoration. No recovery command sent.");return;}
            TemperatureValidation.State original=TemperatureValidation.State.from(record.getJSONObject("original"));
            show("RECOVERY REQUIRES EXPLICIT CONFIRMATION. Original recorded target: "+original.temp+" C. No automatic write on restart.");
            runOnUiThread(()->{if(destroyed)return;AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Restore recorded TV AC temperature?")
                .setMessage("Restore only the recorded original target of "+original.temp+" C to the privately pinned TV AC. First read checks identity, connectivity and current state. No power command or other-device control.")
                .setNegativeButton("Cancel",(d,w)->{}).setPositiveButton("Confirm recovery",(d,w)->job(()->show(validation.recover(true).summary()))).create();
                dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());dialog.show();});
        }));
        recovery.setVisibility(View.GONE); // Initial controls are Read / Prepare / Review only.
        output=new TextView(this);output.setTextSize(16);output.setText(lastText);root.addView(output);
        read.requestFocus(); // No network or command on startup. No automatic recovery.
    }
    private Button button(LinearLayout root,String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);root.addView(b);buttons.add(b);return b;}
    private String readiness(TemperatureValidation.State state){try{validation.fresh(state);return "Freshness and temperature safety checks passed. Preparation and on-screen confirmation still required.";}
        catch(Exception e){return "Write blocked: "+TemperatureValidation.safeFailure(e)+". A cached payload preview may be prepared; a write still requires fresh unchanged original state after confirmation.";}}
    private void ensureSession()throws Exception{
        if(cloud!=null&&validation!=null)return;
        CloudCredentialVault.Account account=vault.load();if(account==null)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED);
        gate=new TemperatureCommandGate();cloud=new GoDaikinCloudClient(new CloudHttpsTransport(gate),SystemClock::elapsedRealtime);
        try{
            cloud.authenticate(account.user,account.password);List<GoDaikinCloudClient.DeviceStatus> devices=cloud.readRegisteredDevices().devices;
            JSONObject binding=vault.loadControl("binding");String pinned=binding==null?null:binding.getString("id");
            List<GoDaikinCloudClient.DeviceStatus> candidates=new ArrayList<>();
            for(GoDaikinCloudClient.DeviceStatus d:devices){
                // Bootstrap uses BOTH the previously verified name/IP and firmware. Subsequent sessions use ONLY the pinned stable ID.
                if(pinned!=null?pinned.equals(d.id):"TV".equalsIgnoreCase(d.name)&&"192.168.50.93".equals(d.ip)&&"V1.2.5".equals(d.firmware))candidates.add(d);
            }
            if(candidates.size()!=1)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);
            target=candidates.get(0);gate.bind(target.id);
            validation=new TemperatureValidation(target.id,new TemperatureValidation.Device(){
                public TemperatureValidation.State read()throws Exception{return readState();}
                public void temperature(int value)throws Exception{gate.permitOnce(target.id,value);try{cloud.submitTemperature(target.id,value);}finally{gate.revoke();}}
            },new TemperatureValidation.Journal(){
                public JSONObject load()throws Exception{return vault.loadControl("journal");}
                public void save(JSONObject value)throws Exception{vault.saveControl("journal",value);}
            },SystemClock::elapsedRealtime,System::currentTimeMillis,Thread::sleep,this::show);
        }catch(Exception e){cloud.close();cloud=null;validation=null;throw e;}
    }
    private TemperatureValidation.State readState()throws Exception{
        String stableId=target.id;GoDaikinCloudClient.DeviceStatus listed=null;
        for(GoDaikinCloudClient.DeviceStatus d:cloud.readRegisteredDevices().devices)if(stableId.equals(d.id)){listed=d;break;}
        if(listed==null)throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.TARGET_MISMATCH);
        listEvidence="Registered-device snapshot: reported="+listed.updatedOn+", state version="+listed.stateVersion+", target="+listed.targetTemperature+" C.";
        TemperatureValidation.State state=new TemperatureValidation.State(cloud.readTargetState(stableId));
        // Every real test read cross-checks the independent registered-device response for this same ID.
        if(listed.targetTemperature==null||state.temp==null||listed.targetTemperature.doubleValue()!=state.temp
            ||listed.power==null||!listed.power.equals(state.power))throw new GoDaikinCloudClient.CloudException(GoDaikinCloudClient.Failure.STALE_STATE);
        return state;
    }
    private void job(Job action){
        if(busy||destroyed)return;busy=true;for(Button b:buttons)b.setEnabled(false);
        worker.execute(()->{try{action.run();}catch(Exception e){show("VALIDATION STOPPED: "+TemperatureValidation.safeFailure(e)
            +"\n"+(cloud==null?"":cloud.safeReadShape())+"\nNo automatic command retry. Check the recorded restoration state before another test.");
            // Re-authenticate only after a new explicit user action, never retry a write here.
            if(e instanceof GoDaikinCloudClient.CloudException){GoDaikinCloudClient.Failure reason=((GoDaikinCloudClient.CloudException)e).reason;
                if(reason==GoDaikinCloudClient.Failure.AUTH_REJECTED||reason==GoDaikinCloudClient.Failure.API_REJECTED||reason==GoDaikinCloudClient.Failure.CREDENTIALS_REQUIRED){if(cloud!=null)cloud.close();cloud=null;validation=null;plan=null;}}
        }finally{
            boolean needsRecovery;
            try{needsRecovery=TemperatureValidation.pending(vault.loadControl("journal"));}catch(Exception e){needsRecovery=true;}
            final boolean recoveryVisible=needsRecovery;busy=false;
            runOnUiThread(()->{if(!destroyed){for(Button b:buttons)b.setEnabled(true);recovery.setVisibility(recoveryVisible?View.VISIBLE:View.GONE);}});
        }});
    }
    private void show(String text){lastText=text;
        // Deliberately excludes cloud IDs, device keys, account identity and tokens.
        try(java.io.FileOutputStream out=openFileOutput("temperature-validation-result.txt",MODE_PRIVATE)){out.write(text.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        runOnUiThread(()->{if(!destroyed&&output!=null)output.setText(text);});
    }
    @Override public void onBackPressed(){
        if(busy){new AlertDialog.Builder(this).setTitle("Validation in progress").setMessage("Please leave this test running until restoration is verified. The original temperature is recorded securely.").setPositiveButton("Stay here",null).show();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){
        destroyed=true;
        // Allow the already approved bounded restoration to complete if the activity is recreated.
        // Never start another transaction or restore automatically after process restart.
        worker.execute(()->{if(cloud!=null)cloud.close();});worker.shutdown();super.onDestroy();
    }
}
