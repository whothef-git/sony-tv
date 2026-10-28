package tv.widgetv.home;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/** Independent, bounded Smart Home content. Activity keeps its existing focus-zone navigation. */
public final class SmartHomeView extends LinearLayout {
    private final DemoSmartHomeRepository demoRepository=new DemoSmartHomeRepository(SystemClock::elapsedRealtime);
    private final RealSmartHomeRepository realRepository;
    private SmartHomeRepository repository;
    private final TextView badge;
    private final RecyclerView list;
    private final GridLayoutManager grid;
    private final DeviceCardAdapter adapter;
    private final TextView empty;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable changed=this::refresh;
    private boolean running,screenActive,observing,cardsFocused;
    private String selectedId="demo-1";
    private int selectedOrder;
    private int columns=4;
    private DeviceControlDialog controls;
    private Dialog developerDialog;
    private Runnable focusCards=()->{};

    public SmartHomeView(Context c,AttributeSet attrs){
        super(c,attrs);setOrientation(VERTICAL);setClipChildren(false);
        realRepository=new RealSmartHomeRepository(c);repository=realRepository;
        badge=new TextView(c);SmartHomeStyle.font(badge,11,SmartHomeStyle.AMBER);
        badge.setText("SMART HOME  ·  DEMO MODE  ·  Simulated devices & readings");
        badge.setPadding(dp(5),0,0,dp(8));addView(badge,new LayoutParams(-1,-2));
        list=new RecyclerView(c);grid=new GridLayoutManager(c,columns);list.setLayoutManager(grid);
        adapter=new DeviceCardAdapter(c,id->{select(id);focusCards.run();openSelected();});list.setAdapter(adapter);
        list.setItemAnimator(null);list.setNestedScrollingEnabled(false);list.setClipToPadding(false);list.setOverScrollMode(OVER_SCROLL_NEVER);
        addView(list,new LayoutParams(-1,0,1));
        empty=new TextView(c);SmartHomeStyle.font(empty,17,SmartHomeStyle.WHITE);empty.setGravity(Gravity.CENTER);
        empty.setText("No Smart Home Devices Connected\n\nDevices will appear automatically\nwhen connected.");
        empty.setVisibility(GONE);addView(empty,new LayoutParams(-1,0,1));setFocusable(true);refresh();
    }
    private int dp(float v){return SmartHomeStyle.dp(getContext(),v);}
    void setFocusCardsCallback(Runnable callback){focusCards=callback;}
    void setScreenActive(boolean active){screenActive=active;if(!active){closeDialogs();setCardsFocused(false);}updateObservation();}
    void setRunning(boolean active){running=active;if(!active)closeDialogs();updateObservation();}
    private void updateObservation(){
        boolean observe=screenActive&&running;
        realRepository.setActive(observe&&!repository.isDemo());
        if(observe!=observing){observing=observe;if(observe)repository.addListener(changed);else{repository.removeListener(changed);handler.removeCallbacks(changed);}}
        if(observe)refresh();
    }
    private void select(String id){SmartDevice d=repository.find(id);if(d!=null){selectedId=id;selectedOrder=d.order;}adapter.highlight(selectedId,cardsFocused);}
    void setCardsFocused(boolean on){cardsFocused=on;adapter.highlight(selectedId,on);if(on)restoreFocus();else list.clearFocus();}
    private int selectedIndex(){for(int i=0;i<adapter.items.size();i++)if(adapter.items.get(i).id.equals(selectedId))return i;return -1;}
    private void restoreFocus(){int i=selectedIndex();if(i<0){requestFocus();return;}list.scrollToPosition(i);list.post(()->{if(!cardsFocused||!screenActive)return;int p=selectedIndex();RecyclerView.ViewHolder h=list.findViewHolderForAdapterPosition(p);if(h!=null)h.itemView.requestFocus();});}
    private void refresh(){
        badge.setText((repository.isDemo()?"DEMO MODE · Simulated data":"LOCAL DEVICES")+" · Manage: MENU / hold OK on Smart Home tab");
        List<SmartDevice> next=repository.visible();boolean found=false;
        for(SmartDevice d:next)if(d.id.equals(selectedId)){found=true;selectedOrder=d.order;}
        if(!found&&!next.isEmpty()){SmartDevice nearest=next.get(0);for(SmartDevice d:next)if(Math.abs(d.order-selectedOrder)<Math.abs(nearest.order-selectedOrder))nearest=d;selectedId=nearest.id;selectedOrder=nearest.order;}
        if(controls!=null){boolean dialogVisible=false;for(SmartDevice d:next)if(d.id.equals(controls.deviceId))dialogVisible=true;if(!dialogVisible)controls.dismiss();}
        adapter.submit(next);adapter.highlight(selectedId,cardsFocused);
        list.setVisibility(next.isEmpty()?GONE:VISIBLE);empty.setVisibility(next.isEmpty()?VISIBLE:GONE);
        setContentDescription("Smart Home. "+(repository.isDemo()?"DEMO MODE. ":"REAL MODE. ")+next.size()+" visible devices. "+repository.statusText());
        handler.removeCallbacks(changed);if(observing){long delay=repository.nextVisibilityDelay();if(delay>=0)handler.postDelayed(changed,delay+1);}
        if(cardsFocused&&controls==null&&developerDialog==null)restoreFocus();
    }
    /** True only when UP exits the first row (including empty state) back to the tabs. */
    boolean move(int key){
        int i=selectedIndex(),n=adapter.items.size();
        if(key==KeyEvent.KEYCODE_DPAD_UP&&(i<columns||i<0))return true;
        if(i<0)return false;
        int target=i;
        if(key==KeyEvent.KEYCODE_DPAD_LEFT&&i>0)target=i-1;
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&i<n-1)target=i+1;
        if(key==KeyEvent.KEYCODE_DPAD_UP&&i>=columns)target=i-columns;
        if(key==KeyEvent.KEYCODE_DPAD_DOWN&&(i/columns+1)*columns<n)target=Math.min(n-1,i+columns);
        select(adapter.items.get(target).id);restoreFocus();return false;
    }
    void openSelected(){if(controls!=null||selectedIndex()<0)return;controls=new DeviceControlDialog(getContext(),repository,selectedId);
        controls.setOnDismissListener(d->{controls=null;if(screenActive&&cardsFocused)restoreFocus();});controls.show();}
    void closeDialogs(){if(controls!=null)controls.dismiss();if(developerDialog!=null)developerDialog.dismiss();}
    View selectedView(){int i=selectedIndex();RecyclerView.ViewHolder h=list.findViewHolderForAdapterPosition(i);return h==null?this:h.itemView;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);
        int count=Math.max(1,Math.min(4,w/dp(185)));if(count!=columns){columns=count;grid.setSpanCount(count);}
        int rows=(8+columns-1)/columns;int card=Math.max(dp(108),Math.min(dp(148),(h-dp(31))/rows-dp(5)));
        if(adapter.cardHeight!=card){adapter.cardHeight=card;adapter.notifyItemRangeChanged(0,adapter.getItemCount());}
    }
    /** Hidden developer entry: remote MENU while Smart Home is selected. No external broadcast. */
    void openDeveloperControls(){
        if(!repository.isDemo()){openManagement();return;}
        if(developerDialog!=null||controls!=null)return;
        List<SmartDevice> all=repository.all();String[] labels=new String[all.size()+3];
        for(int i=0;i<all.size();i++)labels[i]=all.get(i).name+" · "+all.get(i).connection;
        labels[8]="All devices offline (45 second grace)";labels[9]="All devices online";labels[10]="Reset demo";
        AlertDialog dialog=new AlertDialog.Builder(getContext(),android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Developer simulation · DEMO ONLY")
            .setItems(labels,(d,index)->{developerDialog=null;
                if(index<8)openDeveloperDevice(all.get(index).id);
                else if(index==8)demoRepository.setAllConnection(ConnectionState.OFFLINE);
                else if(index==9)demoRepository.setAllConnection(ConnectionState.ONLINE);
                else demoRepository.reset();
            }).setNegativeButton("Close",(d,w)->{}).create();
        developerDialog=dialog;dialog.setOnDismissListener(d->{if(developerDialog==dialog)developerDialog=null;if(cardsFocused)restoreFocus();});dialog.show();
    }
    private void openDeveloperDevice(String id){
        String[] actions={"Online","Offline (45 second grace)","Reconnecting","Power ON","Power OFF","Temperature −","Temperature +","Toggle outlet 1","Toggle outlet 2","Toggle outlet 3"};
        AlertDialog dialog=new AlertDialog.Builder(getContext(),android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Developer · "+repository.find(id).name).setItems(actions,(d,i)->{
                if(i==0)demoRepository.setConnection(id,ConnectionState.ONLINE);
                else if(i==1)demoRepository.setConnection(id,ConnectionState.OFFLINE);
                else if(i==2)demoRepository.setConnection(id,ConnectionState.CONNECTING);
                else if(i==3)demoRepository.setPower(id,PowerState.ON);
                else if(i==4)demoRepository.setPower(id,PowerState.OFF);
                else if(i==5||i==6)repository.adjustTemperature(id,i==5?-1:1);
                else repository.toggleOutlet(id,i-7);
            }).setNegativeButton("Close",(d,w)->{}).create();
        developerDialog=dialog;dialog.setOnDismissListener(d->{if(developerDialog==dialog)developerDialog=null;if(cardsFocused)restoreFocus();});dialog.show();
    }
    void openManagement(){
        if(developerDialog!=null||controls!=null)return;
        DeviceManagementDialog dialog=new DeviceManagementDialog(getContext(),realRepository,repository.isDemo(),()->{
            developerDialog=null;
            AlertDialog confirm=new AlertDialog.Builder(getContext()).setTitle(repository.isDemo()?"Return to real devices?":"Switch display to demo mode?")
                .setMessage("Demo and real devices are separate. Real connection failures never switch to demo automatically.")
                .setPositiveButton("Switch",(d,w)->switchMode()).setNegativeButton("Cancel",null).create();
            developerDialog=confirm;confirm.setOnDismissListener(d->{if(developerDialog==confirm)developerDialog=null;});confirm.show();
        },()->{developerDialog=null;openDeveloperControls();});
        developerDialog=dialog;dialog.setOnDismissListener(d->{if(developerDialog==dialog)developerDialog=null;if(cardsFocused)restoreFocus();});dialog.show();
    }
    private void switchMode(){repository.removeListener(changed);observing=false;realRepository.setActive(false);repository=repository.isDemo()?realRepository:demoRepository;selectedId="";selectedOrder=0;updateObservation();refresh();}
    void shutdown(){setRunning(false);realRepository.shutdown();}
    @Override protected void onDetachedFromWindow(){setRunning(false);handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
}
