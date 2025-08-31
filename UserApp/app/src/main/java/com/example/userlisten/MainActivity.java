package com.example.userlisten;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.*;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private AudioTrack remoteAudio;
    private Socket socket;
    private final String SIGNAL_URL = "https://signal.yourdomain.com"; // এখানে আপনার signaling server URL দিন

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Toast.makeText(this, "Connecting to Live Stream...", Toast.LENGTH_LONG).show();

        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(null);
        peerConnection = factory.createPeerConnection(config, new CustomPeerObserver());

        try {
            socket = IO.socket(SIGNAL_URL);
            socket.connect();
        } catch (URISyntaxException e) { e.printStackTrace(); }

        socket.on("offer", args -> {
            runOnUiThread(() -> {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.OFFER, obj.getString("sdp"));
                    peerConnection.setRemoteDescription(new CustomSdpObserver("setRemote"), sdp);

                    peerConnection.createAnswer(new CustomSdpObserver("createAnswer") {
                        @Override
                        public void onCreateSuccess(SessionDescription sdp) {
                            peerConnection.setLocalDescription(new CustomSdpObserver("setLocal"), sdp);
                            try {
                                JSONObject ans = new JSONObject();
                                ans.put("type", "answer");
                                ans.put("sdp", sdp.description);
                                socket.emit("answer", ans);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }, new MediaConstraints());
                } catch (Exception e) { e.printStackTrace(); }
            });
        });
    }

    private class CustomPeerObserver implements PeerConnection.Observer {
        @Override public void onAddStream(MediaStream mediaStream) {
            if (mediaStream.audioTracks.size() > 0) {
                remoteAudio = mediaStream.audioTracks.get(0);
                remoteAudio.setEnabled(true);
            }
        }
        @Override public void onIceCandidate(IceCandidate iceCandidate) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                obj.put("sdpMid", iceCandidate.sdpMid);
                obj.put("candidate", iceCandidate.sdp);
                socket.emit("candidate", obj);
            } catch (Exception e) { e.printStackTrace(); }
        }
        @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {}
        @Override public void onDataChannel(DataChannel dataChannel) {}
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
        @Override public void onIceConnectionReceivingChange(boolean b) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
        @Override public void onRemoveStream(MediaStream mediaStream) {}
        @Override public void onRenegotiationNeeded() {}
        @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
        @Override public void onTrack(RtpTransceiver transceiver) {}
    }
}
