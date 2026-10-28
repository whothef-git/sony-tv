package tv.widgetv.home;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Device protocol adapter. Reads actual state before/after commands; no optimistic power changes. */
final class TpLinkDeviceClient implements SmartDeviceDriver {
    private final Context context;private final String ip,protocol;
    private final TpLinkSession session;
    private final String terminal;
    TpLinkDeviceClient(Context c,SmartDevice device,SmartCredentialStore.Credentials credentials)throws Exception{
        context=c.getApplicationContext();ip=device.ip;protocol=device.protocol;LanDeviceHttp.validate(ip);
        terminal=java.util.Base64.getEncoder().encodeToString(TpLinkCrypto.hash("MD5",TpLinkCrypto.bytes(java.util.UUID.randomUUID().toString())));
        if(protocol.equals("UNSUPPORTED"))throw new TpLinkSession.ProtocolException("Unsupported advertised firmware protocol. Enable compatible local access in the TP-Link app or update this adapter.");
        session=protocol.equals("XOR")?null:new TpLinkSession(new LanDeviceHttp(c,ip),credentials==null?null:credentials.username,credentials==null?null:credentials.password,protocol);
    }
    private boolean iot(){return protocol.equals("XOR")||protocol.endsWith("-IOT");}
    private JSONObject send(JSONObject data)throws Exception{
        if(session!=null)return session.request(data);
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);Network network=cm.getActiveNetwork();if(network==null)throw new java.io.IOException("TV network is disconnected");
        try(Socket socket=new Socket()){
            network.bindSocket(socket);socket.connect(new InetSocketAddress(ip,9999),2000);socket.setSoTimeout(3000);
            byte[] encoded=TpLinkCrypto.xor(TpLinkCrypto.bytes(data.toString()),false);DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeInt(encoded.length);out.write(encoded);out.flush();
            DataInputStream in=new DataInputStream(socket.getInputStream());int length=in.readInt();if(length<2||length>262144)throw new java.io.IOException("Invalid device response length");byte[] reply=new byte[length];in.readFully(reply);return new JSONObject(new String(TpLinkCrypto.xor(reply,true),StandardCharsets.UTF_8));
        }
    }
    private JSONObject smart(String method,JSONObject params)throws Exception{
        JSONObject request=new JSONObject().put("method",method).put("request_time_milis",System.currentTimeMillis()).put("terminal_uuid",terminal);if(params!=null)request.put("params",params);
        return TpLinkSession.check(send(request)).optJSONObject("result");
    }
    private JSONObject legacy(String method,JSONObject params,String childId)throws Exception{
        JSONObject request=TpLinkState.iotRequest(method,params,childId);
        JSONObject result=send(request).getJSONObject("system").getJSONObject(method);int code=result.optInt("err_code",0);if(code!=0)throw new java.io.IOException("Device rejected outlet request (code "+code+")");return result;
    }
    public SmartDevice read(SmartDevice saved)throws Exception{
        if(saved.type==DeviceType.DAIKIN)throw new java.io.IOException("GO DAIKIN compatibility is not verified");
        JSONObject info=iot()?legacy("get_sysinfo",null,null):smart("get_device_info",null);if(info==null)throw new java.io.IOException("Device returned no state");
        SmartDevice actual=TpLinkState.parse(saved,info);
        if(saved.type==DeviceType.KASA_KP303){
            if(!iot())throw new java.io.IOException("This KP303 firmware needs an unverified SMART child-device protocol. No outlet command will be sent.");
        }else{
            if(iot())throw new java.io.IOException("P110 legacy IOT firmware is not supported by this adapter");
            try{TpLinkState.energy(actual,smart("get_energy_usage",null));}
            catch(Exception unsupported){actual.error="Connected; energy reading unavailable for this firmware.";}
        }return actual;
    }
    public SmartDevice setPower(SmartDevice device,boolean on)throws Exception{
        SmartDevice before=read(device);smart("set_device_info",new JSONObject().put("device_on",on));SmartDevice actual=read(before);
        if(actual.power!=(on?PowerState.ON:PowerState.OFF))throw new java.io.IOException("Device did not confirm the requested power state");return actual;
    }
    public SmartDevice setOutlet(SmartDevice device,int index,boolean on)throws Exception{
        SmartDevice before=read(device);String child=before.outletIds[index];if(child.isEmpty())throw new java.io.IOException("Outlet identifier is missing; refresh the device");
        legacy("set_relay_state",new JSONObject().put("state",on?1:0),child);SmartDevice actual=read(before);
        if(actual.outlets[index]!=(on?PowerState.ON:PowerState.OFF))throw new java.io.IOException("Outlet did not confirm the requested state");return actual;
    }
}
