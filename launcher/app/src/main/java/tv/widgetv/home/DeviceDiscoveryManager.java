package tv.widgetv.home;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import org.json.JSONObject;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bounded UDP broadcast/unicast discovery on the active Wi-Fi or Ethernet network. No multicast. */
final class DeviceDiscoveryManager {
    private final Context context;
    private TpLinkDiscoveryCrypto crypto;
    DeviceDiscoveryManager(Context c){context=c.getApplicationContext();}
    List<SmartDevice> discover(List<SmartDevice> saved)throws Exception{
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);Network network=cm.getActiveNetwork();
        if(network==null)throw new java.io.IOException("TV network is disconnected");
        Set<String> targets=new LinkedHashSet<>();targets.add("255.255.255.255");
        LinkProperties links=cm.getLinkProperties(network);if(links!=null)for(LinkAddress a:links.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&a.getPrefixLength()>=8&&a.getPrefixLength()<=30){
            byte[] address=a.getAddress().getAddress();int value=java.nio.ByteBuffer.wrap(address).getInt(),mask=-1<<(32-a.getPrefixLength());
            targets.add(InetAddress.getByAddress(java.nio.ByteBuffer.allocate(4).putInt((value&mask)|~mask).array()).getHostAddress());
        }
        for(SmartDevice d:saved)if(!d.disabled&&!d.ip.isEmpty()){try{LanDeviceHttp.validate(d.ip);targets.add(d.ip);}catch(Exception ignored){}}
        LinkedHashMap<String,SmartDevice> found=new LinkedHashMap<>();
        if(crypto==null)crypto=new TpLinkDiscoveryCrypto();
        byte[] legacy=TpLinkCrypto.xor(TpLinkCrypto.bytes("{\"system\":{\"get_sysinfo\":{}}}"),false);
        byte[] modern=new byte[]{2,0,0,1,0,0,0,0,0,0,0,0,0x46,0x3c,(byte)0xb5,(byte)0xd3};
        byte[] keyed=crypto.query();
        try(DatagramSocket socket=new DatagramSocket()){
            network.bindSocket(socket);socket.setBroadcast(true);socket.setSoTimeout(400);
            for(int repeat=0;repeat<2;repeat++)for(String target:targets){InetAddress address=InetAddress.getByName(target);
                socket.send(new DatagramPacket(legacy,legacy.length,address,9999));socket.send(new DatagramPacket(modern,modern.length,address,20002));socket.send(new DatagramPacket(keyed,keyed.length,address,20002));socket.send(new DatagramPacket(keyed,keyed.length,address,20004));}
            long until=android.os.SystemClock.elapsedRealtime()+3200;
            while(android.os.SystemClock.elapsedRealtime()<until&&!Thread.currentThread().isInterrupted()){
                DatagramPacket packet=new DatagramPacket(new byte[65507],65507);
                try{socket.receive(packet);}catch(java.net.SocketTimeoutException wait){continue;}
                if(packet.getPort()!=9999&&packet.getPort()!=20002&&packet.getPort()!=20004)continue;
                try{String ip=packet.getAddress().getHostAddress();LanDeviceHttp.validate(ip);
                    byte[] bytes=java.util.Arrays.copyOf(packet.getData(),packet.getLength());
                    JSONObject message=new JSONObject(new String(packet.getPort()==9999?TpLinkCrypto.xor(bytes,true):java.util.Arrays.copyOfRange(bytes,16,bytes.length),StandardCharsets.UTF_8));
                    if(packet.getPort()!=9999)message=crypto.unwrap(message);
                    SmartDevice device=parse(message,ip,packet.getPort()==9999);if(device!=null){String identity=!device.mac.isEmpty()?DeviceRegistry.mac(device.mac):device.hardwareId;if(!identity.isEmpty())found.put(identity,device);}
                }catch(Exception malformed){/* Ignore unrelated/malformed LAN packets, never log their contents. */}
            }
        }return new ArrayList<>(found.values());
    }
    static SmartDevice parse(JSONObject message,String ip,boolean legacy)throws Exception{
        JSONObject info=legacy?message.optJSONObject("system"):message.optJSONObject("result");
        if(legacy&&info!=null)info=info.optJSONObject("get_sysinfo");if(info==null)return null;
        String model=info.optString("model",info.optString("device_model"));DeviceType type=model.startsWith("P110")?DeviceType.TAPO_P110:model.startsWith("KP303")?DeviceType.KASA_KP303:null;
        if(type==null)return null;
        SmartDevice d=DeviceRegistry.blank(type,model,0);d.ip=ip;d.mac=DeviceRegistry.mac(info.optString("mac",info.optString("mic_mac")));d.hardwareId=info.optString("deviceId",info.optString("device_id"));
        if(legacy){d.name=info.optString("alias",model);d.protocol="XOR";}
        else {JSONObject scheme=info.optJSONObject("mgt_encrypt_schm");String enc=scheme==null?"":scheme.optString("encrypt_type");int version=scheme==null?2:scheme.optInt("lv",2);
            String family=info.optString("device_type");
            d.protocol=enc.equals("KLAP")?(version==1?"KLAP1":"KLAP2")+(family.startsWith("IOT.")?"-IOT":""):enc.equals("AES")?(version==2?"AES2":"AES1"):enc.isEmpty()?"AUTO":"UNSUPPORTED";
            if(d.protocol.equals("UNSUPPORTED"))d.error="Advertised firmware protocol is not supported. Check TP-Link third-party compatibility.";
        }return d;
    }
}
