package tv.widgetv.home;

import android.net.ConnectivityManager;
import android.net.Network;
import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;

/** Cleartext HTTP only to validated local numeric IPv4 addresses; TLS defaults are untouched. */
final class LanDeviceHttp implements TpLinkSession.Http {
    private final Context context;private final String ip;private String cookie="";
    LanDeviceHttp(Context c,String ip)throws Exception{this.context=c.getApplicationContext();validate(ip);this.ip=ip;}
    static void validate(String ip)throws Exception{
        if(ip==null||!ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new java.io.IOException("Enter a local IPv4 address, not a URL or hostname");
        String[] parts=ip.split("\\.");for(String p:parts)if(Integer.parseInt(p)>255)throw new java.io.IOException("Invalid IP address");
        InetAddress address=InetAddress.getByName(ip);if(!(address.isSiteLocalAddress()||address.isLinkLocalAddress())||address.isMulticastAddress())throw new java.io.IOException("Device address must be on a private/local network");
    }
    public byte[] post(String path,byte[] data,boolean json)throws Exception{
        if(!path.startsWith("/app")||path.contains("\r")||path.contains("\n"))throw new java.io.IOException("Invalid device path");
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);Network network=manager.getActiveNetwork();
        if(network==null)throw new java.io.IOException("TV network is disconnected");
        HttpURLConnection connection=(HttpURLConnection)network.openConnection(new URL("http://"+ip+path));
        connection.setConnectTimeout(2000);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type",json?"application/json":"application/octet-stream");
        if(!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);connection.setFixedLengthStreamingMode(data.length);
        try{try(java.io.OutputStream output=connection.getOutputStream()){output.write(data);}
            int status=connection.getResponseCode();if(status!=200)throw new TpLinkSession.ProtocolException(status==401||status==403?"Device authentication/session rejected. Check TP-Link account and compatibility settings.":"Device HTTP request failed (status "+status+")");
            for(java.util.Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(entry.getKey()))for(String value:entry.getValue()){
                String part=value.split(";",2)[0];if(part.startsWith("TP_SESSIONID=")||part.startsWith("SESSIONID="))cookie=part;
            }
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>262144)throw new java.io.IOException("Oversized device response");out.write(buffer,0,count);}return out.toByteArray();
            }
        }finally{connection.disconnect();}
    }
}
