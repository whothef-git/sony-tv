package tv.widgetv.home;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight, native remote-focusable management; credentials are entered in a secure window. */
final class DeviceManagementDialog extends Dialog {
    private final RealSmartHomeRepository repository;
    private final LinearLayout body;
    private final TextView status;
    private final Map<String,Button> devices=new LinkedHashMap<>();
    private final Runnable changed=this::refresh;
    private String signature="";
    private final Runnable switchMode,demoTools;
    private final boolean demo;
    private final Button account;
    private final List<Dialog> children=new ArrayList<>();
    DeviceManagementDialog(Context c,RealSmartHomeRepository repository,boolean demo,Runnable switchMode,Runnable demoTools){
        super(c);requestWindowFeature(Window.FEATURE_NO_TITLE);this.repository=repository;this.switchMode=switchMode;this.demoTools=demoTools;this.demo=demo;
        body=new LinearLayout(c);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(18),dp(18),dp(18));body.setBackground(SmartHomeStyle.panel(c,true));
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(c);SmartHomeStyle.font(title,21,SmartHomeStyle.WHITE);title.setText("Manage Devices · "+(demo?"Demo display / Real registry":"Local devices"));body.addView(title);
        status=new TextView(c);SmartHomeStyle.font(status,12,SmartHomeStyle.BLUE);body.addView(status);
        button("Scan for devices",repository::requestScan);
        button("Add device manually",()->chooseType());
        account=button("TP-Link account · Enter/update privately on TV",this::credentials);
        button("GO DAIKIN account · Enter/update privately on TV",()->credentials(true));
        button("Remove stored TP-Link account",()->track(new AlertDialog.Builder(c).setTitle("Remove encrypted TP-Link credentials?").setMessage("Devices will require setup again. No device will be switched off.").setPositiveButton("Remove",(d,w)->repository.clearCredentials()).setNegativeButton("Cancel",null).create()));
        button(demo?"Developer: return to REAL mode":"Developer: switch to DEMO mode",()->{dismiss();switchMode.run();});
        if(demo)button("Developer simulation controls",()->{dismiss();demoTools.run();});
        refresh();
    }
    private int dp(float v){return SmartHomeStyle.dp(getContext(),v);}
    private Button button(String label,Runnable action){Button b=new Button(getContext());b.setAllCaps(false);SmartHomeStyle.font(b,13,SmartHomeStyle.WHITE);b.setText(label);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(12),0,dp(12),0);b.setBackground(SmartHomeStyle.panel(getContext(),false));b.setOnFocusChangeListener((v,on)->b.setBackground(SmartHomeStyle.panel(getContext(),on)));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));lp.topMargin=dp(5);body.addView(b,lp);return b;}
    private void track(Dialog dialog){children.add(dialog);dialog.setOnDismissListener(d->children.remove(dialog));dialog.show();}
    private void refresh(){status.setText(repository.statusText()+"\nGO DAIKIN: "+(repository.hasDaikinAccount()?"account saved; TV AC controls, other ACs read-only":"sign in once on this launcher"));
        account.setText("TP-Link account · "+(repository.hasAccount()?"Saved encrypted on this TV":"Setup required")+" · Enter/update privately");
        List<SmartDevice> all=repository.all();StringBuilder ids=new StringBuilder();for(SmartDevice d:all)ids.append(d.id).append('|');
        if(!signature.equals(ids.toString())){String focus="";for(Map.Entry<String,Button> e:devices.entrySet())if(e.getValue().hasFocus())focus=e.getKey();for(Button b:devices.values())body.removeView(b);devices.clear();for(SmartDevice d:all)devices.put(d.id,button("",()->details(d.id)));signature=ids.toString();if(devices.containsKey(focus))devices.get(focus).requestFocus();}
        for(SmartDevice d:all){Button b=devices.get(d.id);if(b!=null)b.setText(d.name+(d.room.isEmpty()?"":" · "+d.room)+"  |  "+d.connectionLabel()+(d.pending?" · Pending":""));}
    }
    private void chooseType(){track(new AlertDialog.Builder(getContext()).setTitle("Device type").setItems(new String[]{"Tapo P110","Kasa KP303","GO DAIKIN cloud account"},(d,i)->form(null,i==0?DeviceType.TAPO_P110:i==1?DeviceType.KASA_KP303:DeviceType.DAIKIN)).setNegativeButton("Cancel",null).create());}
    private void details(String id){SmartDevice d=repository.find(id);if(d==null)return;
        String when=d.lastSeen==0?"Never verified":java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(d.lastSeen));
        String message="Connection: "+d.connectionLabel()+"\nIP: "+d.ip+"\nProtocol: "+d.protocol+"\nDevice identifier: "+(d.type==DeviceType.DAIKIN?"Private cloud registration":d.hardwareId)+"\nMAC: "+d.mac+"\nLast confirmed: "+when+"\n"+d.error;
        track(new AlertDialog.Builder(getContext()).setTitle(d.name).setItems(new String[]{"View connection / setup status","Rename device","Assign room","Connection settings","Refresh connection","Move earlier","Move later","Remove saved device"},(dialog,index)->{
            if(index==0)track(new AlertDialog.Builder(getContext()).setTitle("Connection / Setup").setMessage(message).setPositiveButton("Close",null).create());
            else if(index==1)editText("Rename device",d.name,value->repository.change(id,value,null,0));
            else if(index==2)editText("Assign room",d.room,value->repository.change(id,null,value,0));
            else if(index==3)form(d,d.type);
            else if(index==4)repository.refreshDevice(id);
            else if(index==5||index==6)repository.change(id,null,null,index==5?-1:1);
            else track(new AlertDialog.Builder(getContext()).setTitle("Remove "+d.name+"?").setMessage("Removes this saved configuration and suppresses automatic re-addition. Add manually to restore it. No power command is sent.").setPositiveButton("Remove",(a,b)->repository.remove(id)).setNegativeButton("Cancel",null).create());
        }).setNegativeButton("Close",null).create());
    }
    private interface SaveText{void save(String value);}
    private void editText(String title,String initial,SaveText save){EditText field=field(title,initial,false);track(new AlertDialog.Builder(getContext()).setTitle(title).setView(field).setPositiveButton("Save",(d,w)->save.save(field.getText().toString().trim())).setNegativeButton("Cancel",null).create());}
    private EditText field(String hint,String value,boolean secret){EditText e=new EditText(getContext());SmartHomeStyle.font(e,14,SmartHomeStyle.WHITE);e.setHint(hint);e.setHintTextColor(SmartHomeStyle.BLUE);e.setText(value);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(secret?512:254)});e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);return e;}
    private LinearLayout fields(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(6),dp(18),dp(6));return l;}
    private void form(SmartDevice device,DeviceType type){
        if(type==DeviceType.DAIKIN){credentials(true);return;}
        LinearLayout fields=fields();EditText name=field("Device name",device==null?type==DeviceType.TAPO_P110?"Tapo P110":type==DeviceType.KASA_KP303?"Kasa Power Strip":"Daikin AC":device.name,false),room=field("Room",device==null?"":device.room,false),ip=field("Local IPv4 address",device==null?"":device.ip,false);
        fields.addView(name);fields.addView(room);fields.addView(ip);
        String[] protocols=type==DeviceType.KASA_KP303?new String[]{"XOR","AUTO","KLAP1-IOT","KLAP2-IOT"}:new String[]{"AUTO","KLAP2","KLAP1","AES1","AES2"};int[] chosen={0};for(int i=0;i<protocols.length;i++)if(device!=null&&protocols[i].equals(device.protocol))chosen[0]=i;
        Button protocol=new Button(getContext());protocol.setAllCaps(false);protocol.setText("Protocol: "+protocols[chosen[0]]);protocol.setOnClickListener(v->{chosen[0]=(chosen[0]+1)%protocols.length;protocol.setText("Protocol: "+protocols[chosen[0]]);});fields.addView(protocol);
        TextView error=new TextView(getContext());SmartHomeStyle.font(error,12,SmartHomeStyle.AMBER);error.setText(type==DeviceType.DAIKIN?"GO DAIKIN is disabled until controller model/API compatibility is verified.":"Use discovery protocol where possible. Enter TP-Link credentials separately.");fields.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(getContext()).setTitle(device==null?"Add device":"Connection settings").setView(fields).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        track(dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);
            repository.configure(device==null?null:device.id,type,name.getText().toString().trim(),room.getText().toString().trim(),ip.getText().toString().trim(),protocols[chosen[0]],problem->{if(!dialog.isShowing())return;if(problem.isEmpty())dialog.dismiss();else{error.setText(problem);save.setEnabled(true);}});
        });
    }
    private void credentials(){credentials(false);}
    private void credentials(boolean daikin){
        String provider=daikin?"GO DAIKIN":"TP-Link";
        LinearLayout fields=fields();EditText user=field(provider+" account email","",false),password=field(provider+" password","",true);user.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);user.setSaveEnabled(false);password.setSaveEnabled(false);fields.addView(user);fields.addView(password);
        TextView note=new TextView(getContext());SmartHomeStyle.font(note,12,SmartHomeStyle.BLUE);note.setText(daikin?"Encrypted on this TV with Android Keystore. Connects directly to GO DAIKIN cloud. TV AC controls only; other ACs read-only.":"Encrypted on this TV with Android Keystore. Local-device authentication only; no cloud login. Enable third-party compatibility if required by firmware.");fields.addView(note);
        AlertDialog dialog=new AlertDialog.Builder(getContext()).setTitle("Private "+provider+" setup").setView(fields).setPositiveButton("Save securely",null).setNegativeButton("Cancel",null).create();
        children.add(dialog);dialog.setOnDismissListener(d->{password.getText().clear();user.getText().clear();children.remove(dialog);});
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String username=user.getText().toString(),secret=password.getText().toString();if(username.isEmpty()||secret.isEmpty()){note.setText("Enter both your account email and password.");return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);java.util.function.Consumer<String> done=problem->{password.getText().clear();if(!dialog.isShowing())return;if(problem.isEmpty())dialog.dismiss();else{note.setText(problem);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}};
            if(daikin)repository.saveDaikinCredentials(username,secret,done);else repository.saveCredentials(username,secret,done);
        });
    }
    @Override protected void onStart(){super.onStart();Window w=getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(getContext().getResources().getDisplayMetrics().widthPixels*.86f),(int)(getContext().getResources().getDisplayMetrics().heightPixels*.86f));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}repository.addListener(changed);if(body.getChildCount()>2)body.getChildAt(2).requestFocus();}
    @Override protected void onStop(){repository.removeListener(changed);for(Dialog child:new ArrayList<>(children))child.dismiss();children.clear();super.onStop();}
}
