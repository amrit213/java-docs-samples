/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openshift.quickstarts.undertow.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.TargetDataLine;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

/**
 * @author Stuart Douglas
 */
public class SpeechServlet extends HttpServlet {

    public static final String MESSAGE = "message";

    private String message;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        message = config.getInitParameter(MESSAGE);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        
    	ResponseObserver<StreamingRecognizeResponse> responseObserver = null;
        try (SpeechClient client = SpeechClient.create()) {

          responseObserver =
              new ResponseObserver<StreamingRecognizeResponse>() {
                ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

                public void onStart(StreamController controller) {}

                public void onResponse(StreamingRecognizeResponse response) {
                  responses.add(response);
                }

                public void onComplete() {
                  for (StreamingRecognizeResponse response : responses) {
                    StreamingRecognitionResult result = response.getResultsList().get(0);
                    SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                    System.out.printf("Transcript : %s\n", alternative.getTranscript());
                  }
                }

                public void onError(Throwable t) {
                  System.out.println(t);
                }
              };

          ClientStream<StreamingRecognizeRequest> clientStream =
              client.streamingRecognizeCallable().splitCall(responseObserver);

          RecognitionConfig recognitionConfig =
              RecognitionConfig.newBuilder()
                  .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                  .setLanguageCode("en-US")
                  .setSampleRateHertz(16000)
                  .build();
          StreamingRecognitionConfig streamingRecognitionConfig =
              StreamingRecognitionConfig.newBuilder().setConfig(recognitionConfig).build();

          StreamingRecognizeRequest request =
              StreamingRecognizeRequest.newBuilder()
                  .setStreamingConfig(streamingRecognitionConfig)
                  .build(); // The first request in a streaming call has to be a config

          clientStream.send(request);
          // SampleRate:16000Hz, SampleSizeInBits: 16, Number of channels: 1, Signed: true,
          // bigEndian: false
          AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
          DataLine.Info targetInfo =
              new Info(
                  TargetDataLine.class,
                  audioFormat); // Set the system information to read from the microphone audio stream

          if (!AudioSystem.isLineSupported(targetInfo)) {
            System.out.println("Microphone not supported");
            //System.exit(0);
          }
          // Target data line captures the audio stream the microphone produces.
          TargetDataLine targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
          targetDataLine.open(audioFormat);
          targetDataLine.start();
          System.out.println("Start speaking");
          long startTime = System.currentTimeMillis();
          // Audio Input Stream
          AudioInputStream audio = new AudioInputStream(targetDataLine);
          while (true) {
            long estimatedTime = System.currentTimeMillis() - startTime;
            byte[] data = new byte[6400];
            audio.read(data);
            if (estimatedTime > 60000) { // 60 seconds
              System.out.println("Stop speaking.");
              targetDataLine.stop();
              targetDataLine.close();
              break;
            }
            request =
                StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(data))
                    .build();
            clientStream.send(request);
          }
        } catch (Exception e) {
          System.out.println(e);
        }
        responseObserver.onComplete();
    	
    	
    	//PrintWriter writer = resp.getWriter();
        //writer.write("speak!");
        //writer.close();
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
