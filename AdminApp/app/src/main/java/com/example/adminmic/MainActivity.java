package com.example.adminmic;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.webrtc.*;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private Socket socket;
    private final String SIGNAL_URL = "https://signal.yourdomain.com"; // এখানে আপনার signaling server URL দিন

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button btn = new Button(this);
        btn.setText("Start Mic Broadcast");
        setContentView(btn);

        btn.setOnClickListener(v -> startBroadcast());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        }
    }

    private void startBroadcast() {
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(null);
        peerConnection = factory.createPeerConnection(config, new CustomPeerObserver());

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = factory.createAudioTrack("101", audioSource);
        MediaStream stream = factory.createLocalMediaStream("stream");
        stream.addTrack(audioTrack);
        peerConnection.addStream(stream);

        try {
            socket = IO.socket(SIGNAL_URL);
            socket.connect();
        } catch (URISyntaxException e) { e.printStackTrace(); }

        peerConnection.createOffer(new CustomSdpObserver("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new CustomSdpObserver("setLocal"), sdp);
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("type", "offer");
                    obj.put("sdp", sdp.description);
                    socket.emit("offer", obj);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }, new MediaConstraints());
    }

    private class CustomPeerObserver implements PeerConnection.Observer {
        @Override public void onIceCandidate(IceCandidate iceCandidate) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                obj.put("sdpMid", iceCandidate.sdpMid);
                obj.put("candidate", iceCandidate.sdp);
                socket.emit("candidate", obj);
            } catch (Exception e) { e.printStackTrace(); }
        }
        @Override public void onAddStream(MediaStream mediaStream) {}
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
