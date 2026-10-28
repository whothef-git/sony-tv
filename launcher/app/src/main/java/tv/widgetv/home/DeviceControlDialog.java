package tv.widgetv.home;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Native modal: its window owns D-pad focus; updates keep the same control views. */
final class DeviceControlDialog extends Dialog {
    private final SmartHomeRepository repository;
    final String deviceId;
    private final LinearLayout body;
    private final TextView status,reading;
    private final List<Runnable> updates=new ArrayList<>();
    private final List<List<Button>> rows=new ArrayList<>();
    private final List<Button> buttons=new ArrayList<>();
    private final Runnable listener=this::refresh;
    DeviceControlDialog(Context c,SmartHomeRepository repo,String id) {
        super(c);repository=repo;deviceId=id;requestWindowFeature(Window.FEATURE_NO_TITLE);
        SmartDevice d=repo.find(id);
        body=new LinearLayout(c);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(20),dp(24),dp(20));body.setBackground(SmartHomeStyle.panel(c,true));
        TextView title=label(d.name+" Control",21,SmartHomeStyle.WHITE);body.addView(title);
        status=label("",12,SmartHomeStyle.AMBER);body.addView(status);
        reading=label("",16,SmartHomeStyle.BLUE);LinearLayout.LayoutParams readingLp=new LinearLayout.LayoutParams(-1,-2);readingLp.topMargin=dp(16);body.addView(reading,readingLp);
        if(d.type!=DeviceType.KASA_KP303) {
            row("Power");control(()->"Power: "+current().power,()->{repo.togglePower(id);refresh();});
        }
        if(d.type==DeviceType.DAIKIN) {
            row("Temperature");control(()->"− Decrease",()->{repo.adjustTemperature(id,-1);refresh();});control(()->"+ Increase",()->{repo.adjustTemperature(id,1);refresh();});
            row("Operating mode");for(String mode:repo.isDemo()?new String[]{"Cool","Dry","Fan","Auto"}:new String[]{"Cool","Dry","Fan"})control(()->(current().mode.equals(mode)?"✓ ":"")+mode,()->{repo.setMode(id,mode);refresh();});
            row("Fan speed");for(String speed:new String[]{"Low","Medium","High","Auto"})control(()->(current().fanSpeed.equals(speed)?"✓ ":"")+speed,()->{repo.setFanSpeed(id,speed);refresh();});
        } else if(d.type==DeviceType.KASA_KP303) {
            row("Outlets");for(int i=0;i<3;i++){final int outlet=i;control(()->(repo.isDemo()?"Outlet "+(outlet+1):current().outletNames[outlet])+": "+current().outlets[outlet],()->repo.toggleOutlet(id,outlet));}
        }
        row("");control(()->"Close",this::dismiss);
        buttons.get(buttons.size()-1).setOnClickListener(v->dismiss());
        setContentView(body);setCanceledOnTouchOutside(false);refresh();
    }
    private SmartDevice current(){return repository.find(deviceId);}
    private int dp(float v){return SmartHomeStyle.dp(getContext(),v);}
    private TextView label(String s,float size,int color){TextView t=new TextView(getContext());SmartHomeStyle.font(t,size,color);t.setText(s);return t;}
    private LinearLayout currentRow;
    private void row(String label){
        if(!label.isEmpty()){TextView t=label(label,11,SmartHomeStyle.BLUE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);body.addView(t,lp);}
        currentRow=new LinearLayout(getContext());currentRow.setOrientation(LinearLayout.HORIZONTAL);body.addView(currentRow,new LinearLayout.LayoutParams(-1,-2));rows.add(new ArrayList<>());
    }
    private void control(Supplier<String> text,Runnable action){
        Button b=new Button(getContext());b.setAllCaps(false);SmartHomeStyle.font(b,14,SmartHomeStyle.WHITE);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));
        b.setPadding(dp(8),0,dp(8),0);b.setBackground(SmartHomeStyle.panel(getContext(),false));
        b.setOnFocusChangeListener((v,on)->b.setBackground(SmartHomeStyle.panel(getContext(),on)));
        buttons.add(b);b.setOnClickListener(v->{SmartDevice d=current();if(d!=null&&(!d.pending||d.type==DeviceType.DAIKIN))action.run();});b.setOnKeyListener((v,code,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            int r=-1,x=-1;for(int i=0;i<rows.size();i++){int j=rows.get(i).indexOf(b);if(j>=0){r=i;x=j;break;}}
            if(code==KeyEvent.KEYCODE_DPAD_UP&&r>0){rows.get(r-1).get(Math.min(x,rows.get(r-1).size()-1)).requestFocus();return true;}
            if(code==KeyEvent.KEYCODE_DPAD_DOWN&&r<rows.size()-1){rows.get(r+1).get(Math.min(x,rows.get(r+1).size()-1)).requestFocus();return true;}
            if(code==KeyEvent.KEYCODE_DPAD_LEFT&&x>0){rows.get(r).get(x-1).requestFocus();return true;}
            if(code==KeyEvent.KEYCODE_DPAD_RIGHT&&x<rows.get(r).size()-1){rows.get(r).get(x+1).requestFocus();return true;}
            return code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));currentRow.addView(b,lp);rows.get(rows.size()-1).add(b);
        updates.add(()->b.setText(text.get()));
    }
    private void refresh(){SmartDevice d=current();if(d==null){dismiss();return;}
        status.setText((repository.isDemo()?"DEMO MODE · In-memory controls":d.type==DeviceType.DAIKIN?"GO DAIKIN CLOUD":"LOCAL DEVICE")+" · "+d.connectionLabel()+(d.pending?" · Waiting for fresh state / actual confirmation":d.connection!=ConnectionState.ONLINE?" · Controls unavailable until online":"")+(d.error.isEmpty()?"":"\n"+d.error));
        status.setTextColor(d.connection==ConnectionState.ONLINE&&!d.cached()?SmartHomeStyle.GREEN:SmartHomeStyle.AMBER);
        reading.setText(d.type==DeviceType.DAIKIN?(d.temperature>0?d.temperature+"°C":"—")+"  ·  "+d.mode+"  ·  Fan "+d.fanSpeed:
            d.type==DeviceType.TAPO_P110?(repository.isDemo()?"Demo usage: ":"Last confirmed usage: ")+DeviceCardAdapter.reading(d.watts,0)+" W  ·  "+DeviceCardAdapter.reading(d.dailyKwh,2)+" kWh today":repository.isDemo()?"Three independent demo outlets":"Lampu Tidur · iPhone Charger · Laptop");
        for(Runnable update:updates)update.run();
        for(int i=0;i<buttons.size()-1;i++){boolean enabled=(d.type==DeviceType.DAIKIN?d.daikinWritable&&d.connection==ConnectionState.ONLINE:!d.pending&&d.connection==ConnectionState.ONLINE&&!d.disabled)&&(repository.isDemo()||d.type!=DeviceType.DAIKIN||d.daikinWritable);buttons.get(i).setClickable(enabled);buttons.get(i).setAlpha(enabled?1f:d.type==DeviceType.DAIKIN?.45f:d.pending?.5f:1f);}
    }
    @Override protected void onStart(){super.onStart();Window w=getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(getContext().getResources().getDisplayMetrics().widthPixels*.86f),-2);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.72f;a.gravity=Gravity.CENTER;w.setAttributes(a);}repository.addListener(listener);rows.get(0).get(0).requestFocus();}
    @Override protected void onStop(){repository.removeListener(listener);super.onStop();}
}
