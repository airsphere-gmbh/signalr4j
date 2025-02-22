/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
See License.txt in the project root for license information.
*/

package com.github.signalr4j.client.http.java;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import com.github.signalr4j.client.Logger;
import com.github.signalr4j.client.http.HttpConnectionFuture;
import com.github.signalr4j.client.http.StreamResponse;
import com.github.signalr4j.client.LogLevel;
import com.github.signalr4j.client.http.Request;

/**
 * Runnable that executes a network operation
 */
class NetworkRunnable implements Runnable {

    HttpURLConnection mConnection = null;
    InputStream mResponseStream = null;
    Logger mLogger;
    Request mRequest;
    HttpConnectionFuture mFuture;
    HttpConnectionFuture.ResponseCallback mCallback;

    Object mCloseLock = new Object();

    /**
     * Initializes the network runnable
     * 
     * @param logger
     *            logger to log activity
     * @param request
     *            The request to execute
     * @param future
     *            Future for the operation
     * @param callback
     *            Callback to invoke after the request execution
     */
    public NetworkRunnable(Logger logger, Request request, HttpConnectionFuture future, HttpConnectionFuture.ResponseCallback callback) {
        mLogger = logger;
        mRequest = request;
        mFuture = future;
        mCallback = callback;
    }

    @Override
    public void run() {
        try {
            int responseCode = -1;
            if (!mFuture.isCancelled()) {
                if (mRequest == null) {
                    mFuture.triggerError(new IllegalArgumentException("request"));
                    return;
                }

                mLogger.log("Execute the HTTP Request", LogLevel.Verbose);
                mRequest.log(mLogger);
                mConnection = createHttpURLConnection(mRequest);

                mLogger.log("Request executed", LogLevel.Verbose);

                responseCode = mConnection.getResponseCode();

                if (responseCode < 400) {
                    mResponseStream = mConnection.getInputStream();
                } else {
                    mResponseStream = mConnection.getErrorStream();
                    if (mResponseStream == null) {
                        mResponseStream = new ByteArrayInputStream(new byte[0]);
                    }
                }
            }
        
            if (mResponseStream != null && !mFuture.isCancelled()) {
                mCallback.onResponse(new StreamResponse(mResponseStream, responseCode, mConnection.getHeaderFields()));
                mFuture.setResult(null);
            }
        } catch (Throwable e) {
            if (!mFuture.isCancelled()) {
                if (mConnection != null) {
                    mConnection.disconnect();
                }

                mLogger.log("Error executing request: " + e.getMessage(), LogLevel.Critical);
                mFuture.triggerError(e);
            }
        } finally {
            closeStreamAndConnection();
        }
    }

    /**
     * Closes the stream and connection, if possible
     */
    void closeStreamAndConnection() {

        try {

            if (mConnection != null) {
                mConnection.disconnect();
            }
            
            if (mResponseStream != null) {
                mResponseStream.close();
            }
        } catch (Exception e) {
        }
    }

    /**
     * Creates an HttpURLConnection
     * 
     * @param request
     *            The request info
     * @return An HttpURLConnection to execute the request
     * @throws java.io.IOException
     */
    static HttpURLConnection createHttpURLConnection(Request request) throws IOException {
        URL url = new URL(request.getUrl());
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // the timeout needs for right disconnection.
        // without it when disconnect() calls at NetworkRunnable.closeStreamAndConnection()
        // there is the deadlock occurred at StreamResponse.readLine()
        connection.setReadTimeout(15 * 1000);
        connection.setConnectTimeout(15 * 1000);
        connection.setRequestMethod(request.getVerb());

        Map<String, String> headers = request.getHeaders();

        for (String key : headers.keySet()) {
            connection.setRequestProperty(key, headers.get(key));
        }

        if (request.getContent() != null) {
            connection.setDoOutput(true);
            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
            String requestContent = request.getContent();
            out.write(requestContent);
            out.close();
        }

        return connection;
    }

}
