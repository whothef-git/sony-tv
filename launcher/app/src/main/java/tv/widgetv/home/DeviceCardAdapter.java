package tv.widgetv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Small native cards with stable RecyclerView IDs; no image downloads or running animations. */
final class DeviceCardAdapter extends RecyclerView.Adapter<DeviceCardAdapter.Holder> {
    final List<SmartDevice> items=new ArrayList<>();
    private final Consumer<String> open;
    String selectedId;
    boolean active;
    int cardHeight;
    DeviceCardAdapter(Context c,Consumer<String> open) {this.open=open;cardHeight=SmartHomeStyle.dp(c,126);setHasStableIds(true);}
    void submit(List<SmartDevice> next) {
        List<SmartDevice> old=new ArrayList<>(items);
        DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
            public int getOldListSize(){return old.size();} public int getNewListSize(){return next.size();}
            public boolean areItemsTheSame(int a,int b){return old.get(a).id.equals(next.get(b).id);}
            public boolean areContentsTheSame(int a,int b){return description(old.get(a)).equals(description(next.get(b)));}
        });
        items.clear();items.addAll(next);diff.dispatchUpdatesTo(this);
    }
    void highlight(String id,boolean on) {
        String before=selectedId;boolean changed=active!=on;selectedId=id;active=on;
        for(int i=0;i<items.size();i++)if(changed||items.get(i).id.equals(id)||items.get(i).id.equals(before))notifyItemChanged(i);
    }
    public long getItemId(int p){return stableId(items.get(p).id);}
    static long stableId(String id){long value=0xcbf29ce484222325L;for(int i=0;i<id.length();i++){value^=id.charAt(i);value*=0x100000001b3L;}return value;}
    public int getItemCount(){return items.size();}
    public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder(new Card(parent.getContext()));}
    public void onBindViewHolder(Holder h,int p) {
        SmartDevice d=items.get(p);Card c=(Card)h.itemView;c.device=d;
        RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,cardHeight);lp.setMargins(5,5,5,5);c.setLayoutParams(lp);
        c.setBackground(SmartHomeStyle.panel(c.getContext(),active&&d.id.equals(selectedId)));
        c.setContentDescription(description(d));c.setOnClickListener(v->open.accept(d.id));c.invalidate();
    }
    static String description(SmartDevice d) {
        String info=d.type==DeviceType.DAIKIN?d.temperature+"°C, "+d.mode+", Fan "+d.fanSpeed:
            d.type==DeviceType.TAPO_P110?reading(d.watts,0)+" W, "+reading(d.dailyKwh,2)+" kWh today":
            "Outlet 1 "+d.outlets[0]+", Outlet 2 "+d.outlets[1]+", Outlet 3 "+d.outlets[2];
        return d.name+", "+d.room+", "+d.brand()+", "+d.connectionLabel()+", "+d.power+", "+info+(d.pending?". Pending confirmation":"")+(d.demo?". DEMO data":". Last confirmed "+d.lastSeen)+". "+d.error;
    }
    static String reading(double number,int precision){return Double.isFinite(number)?String.format(Locale.US,"%."+precision+"f",number):"—";}
    static final class Holder extends RecyclerView.ViewHolder {Holder(View v){super(v);}}
    static final class Card extends View {
        SmartDevice device;final Paint p=new Paint(3);final float density;
        Card(Context c){super(c);density=c.getResources().getDisplayMetrics().density;setFocusable(true);setClickable(true);
            p.setTypeface(android.graphics.Typeface.create(getResources().getFont(R.font.outfit),400,false));p.setFontVariationSettings("'wght' 400");}
        void text(Canvas c,String s,float x,float y,float size,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);c.drawText(s,x,y,p);}
        @Override protected void onDraw(Canvas canvas) {
            if(device==null)return;SmartDevice d=device;
            canvas.save();canvas.scale(density,density);float w=getWidth()/density,h=getHeight()/density;
            // Recognizable local pictograms: air conditioner, plug, three-outlet strip.
            p.setColor(0xFFE7F1F6);p.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(12,15,55,42,4,4,p);p.setColor(0xFF5E849A);p.setStrokeWidth(1);
            if(d.type==DeviceType.DAIKIN){canvas.drawLine(16,33,51,33,p);canvas.drawLine(18,37,49,37,p);}
            else {int slots=d.type==DeviceType.KASA_KP303?3:1;for(int i=0;i<slots;i++){float sx=22+i*11;canvas.drawCircle(sx,27,1.5f,p);canvas.drawLine(sx-2,32,sx+2,32,p);}}
            p.setTextSize(13);
            int separator=d.name.indexOf(" · ");
            String titleName=separator>=0?d.name.substring(0,separator):d.name;
            String title=TextUtils.ellipsize(titleName,new android.text.TextPaint(p),Math.max(30,w-76),TextUtils.TruncateAt.END).toString();
            text(canvas,title,65,25,13,SmartHomeStyle.WHITE);
            String subtitle=d.brand()+(separator>=0?d.name.substring(separator):"");
            p.setTextSize(separator>=0?10:11);
            subtitle=TextUtils.ellipsize(subtitle,new android.text.TextPaint(p),Math.max(30,w-76),TextUtils.TruncateAt.END).toString();
            text(canvas,subtitle,65,43,separator>=0?10:11,SmartHomeStyle.BLUE);
            int connection=d.connection==ConnectionState.ONLINE?SmartHomeStyle.GREEN:SmartHomeStyle.AMBER;
            p.setColor(connection);canvas.drawCircle(16,60,3,p);text(canvas,d.pending?"Confirming…":d.connectionLabel()+(d.connection!=ConnectionState.ONLINE&&d.lastSeen>0?" · cached":""),24,64,10,connection);
            if(d.type==DeviceType.KASA_KP303) {
                for(int i=0;i<3;i++){float x=12+i*(w-24)/3;
                    String label=d.demo?"Outlet "+(i+1):d.outletNames[i];
                    p.setTextSize(9);label=TextUtils.ellipsize(label,new android.text.TextPaint(p),(w-24)/3-3,TextUtils.TruncateAt.END).toString();
                    text(canvas,label,x,h-31,9,SmartHomeStyle.BLUE);
                    text(canvas,d.outlets[i].name(),x,h-12,13,d.outlets[i]==PowerState.ON?SmartHomeStyle.GREEN:SmartHomeStyle.GREY);}
            } else {
                String value=d.type==DeviceType.DAIKIN?d.temperature+"°C":reading(d.watts,0)+" W";
                text(canvas,value,12,h-33,21,SmartHomeStyle.WHITE);
                String detail=d.type==DeviceType.DAIKIN?d.mode:reading(d.dailyKwh,2)+" kWh · Today";
                text(canvas,detail,12,h-13,11,SmartHomeStyle.BLUE);
                p.setColor(d.power==PowerState.ON?0xFF008A66:0xFF263E57);
                canvas.drawRoundRect(w-65,h-48,w-12,h-18,15,15,p);
                text(canvas,d.power.name(),w-53,h-28,13,SmartHomeStyle.WHITE);
            }
            canvas.restore();
        }
    }
}
