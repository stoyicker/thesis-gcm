package com.jorge.thesis.gcm;

import com.jorge.thesis.datamodel.CEntityTagManager;
import com.jorge.thesis.io.file.FileReadUtils;
import com.jorge.thesis.io.net.HTTPRequestsSingleton;
import com.jorge.thesis.util.EnvVars;
import com.jorge.thesis.util.TimeUtils;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public final class GCMCommunicatorSingleton {

    private static final Object LOCK = new Object();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Integer MAX_AMOUNT_OF_IDS_PER_REQUEST = 950; //Must be 1000 or less
    private static final Long TAG_SYNC_REQUEST_INITIAL_DELAY = 55L;
    private static final Integer EXPONENTIAL_WAIT_INCREASE_FACTOR = 2;
    private static volatile GCMCommunicatorSingleton mInstance;
    private final Queue<CDelayedTag> mTagRequestQueue = new LinkedList<>();
    private final Queue<CDelayedRequest> mSyncRequestQueue = new LinkedList<>();

    private GCMCommunicatorSingleton() {
    }

    public static GCMCommunicatorSingleton getInstance() {
        GCMCommunicatorSingleton ret = mInstance;
        if (ret == null) {
            synchronized (LOCK) {
                ret = mInstance;
                if (ret == null) {
                    ret = new GCMCommunicatorSingleton();
                    mInstance = ret;
                }
            }
        }
        return ret;
    }

    /**
     * Queues a sync request for a tag.
     *
     * @param tag {@link com.jorge.thesis.datamodel.CEntityTagManager.CEntityTag} Tag whose sync is
     *            requested.
     * @return <value>TRUE</value> if successful, <value>FALSE</value> if a synchronisation for this tag is already
     * queued.
     */
    public synchronized Boolean queueTagSyncRequest(CEntityTagManager.CEntityTag tag) {
        Boolean ret = Boolean.TRUE;
        final CDelayedTag wrapper = new CDelayedTag(tag, TAG_SYNC_REQUEST_INITIAL_DELAY, TimeUnit
                .MILLISECONDS);

        if (!mTagRequestQueue.contains(wrapper)) {
            mTagRequestQueue.add(wrapper); //Inserts at tail
            onTagRequestAdded();
        } else
            ret = Boolean.FALSE;

        return ret;
    }

    private static class CDelayedTag implements Delayed {
        private CEntityTagManager.CEntityTag mTag;
        private Long mDelay;
        private TimeUnit mDelayUnit;

        public CDelayedTag(CEntityTagManager.CEntityTag _tag, Long _delay, TimeUnit _unit) {
            mTag = _tag;
            mDelay = _delay;
            mDelayUnit = _unit;
        }

        @Override
        public int compareTo(@SuppressWarnings("NullableProblems") Delayed o) {
            if (o instanceof CDelayedTag) {
                return TimeUtils.convertTimeTo(mDelay, mDelayUnit, TimeUnit.MILLISECONDS).compareTo(o.getDelay(TimeUnit
                        .MILLISECONDS));
            } else
                throw new UnsupportedOperationException(getClass().getName() + " objects can only be compared as " +
                        "CEntityDelayedTag " +
                        "among " +
                        "themselves.");
        }

        @Override
        public long getDelay(@SuppressWarnings("NullableProblems") TimeUnit unit) {
            return TimeUtils.convertTimeTo(mDelay, mDelayUnit, unit);
        }

        public CEntityTagManager.CEntityTag getPureTag() {
            return mTag;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CDelayedTag))
                throw new UnsupportedOperationException(getClass().getName() + " objects can only be compared for " +
                        "equality as " +
                        "CDelayedTag " +
                        "among " +
                        "themselves.");
            else
                return getPureTag().equals(((CDelayedTag) obj).getPureTag());
        }
    }

    private synchronized void onTagRequestAdded() {
        final Long INTERRUPTED_TAKE_WAIT_MILLIS = 1000L;
        try {
            sendSyncRequestToAllIds(GCMCommunicatorSingleton.this.mTagRequestQueue.poll());
        } catch (NoSuchElementException e) {
            //Report, take a break and keep trying
            e.printStackTrace(System.err);
            try {
                Thread.sleep(INTERRUPTED_TAKE_WAIT_MILLIS);
            } catch (InterruptedException e1) {
                e.printStackTrace(System.err);
                //Will never happen
            }
            onTagRequestAdded();
        }
    }

    private synchronized void sendSyncRequestToAllIds(CDelayedTag tag) {
        List<CDelayedRequest> requests = createSyncRequests(tag);
        //Inserts at tail
        requests.forEach(this::delayAndQueueRequestForExecution);
    }

    private synchronized List<CDelayedRequest> createSyncRequests(CDelayedTag tag) {
        List<String> targetIds = CEntityTagManager.getTagSubscribedRegistrationIds(tag.getPureTag());
        List<CDelayedRequest> ret = new LinkedList<>();
        final String GOOGLE_GCM_URL;
        try {
            GOOGLE_GCM_URL = IOUtils.toString(FileReadUtils.class.getResourceAsStream
                    ("/gcm_server_url"));
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new IllegalStateException("Resource /gcm_server_url not properly loaded.");
        }
        Integer startIndex;
        for (Integer i = 0; !((startIndex = i * MAX_AMOUNT_OF_IDS_PER_REQUEST) > targetIds.size()); i++) {
            List<String> thisGroupOfIds;
            final Integer lastIndex = (i + 1) *
                    MAX_AMOUNT_OF_IDS_PER_REQUEST;
            if (lastIndex < targetIds.size())
                thisGroupOfIds = targetIds.subList(startIndex, lastIndex);
            else
                thisGroupOfIds = targetIds.subList(startIndex, targetIds.size());
            JSONObject body = new JSONObject();
            try {
                body.put("registration_ids", new JSONArray(thisGroupOfIds));
                JSONObject data = new JSONObject();
                data.put("tag", tag.getPureTag().name());
                body.put("data", data);
            } catch (JSONException e) {
                e.printStackTrace(System.err);
                //Will never happen
            }
            if (EnvVars.API_KEY == null) {
                throw new IllegalStateException("API_KEY environment variable not defined. Please check the " +
                        "technical specification for instructions.");
            }
            ret.add(new CDelayedRequest(new Request.Builder().
                    addHeader("Authorization", "key=" + EnvVars.API_KEY).
                    addHeader("Content-Type", "application/json").
                    url(GOOGLE_GCM_URL).
                    post(RequestBody.create(JSON, body.toString())).build(), tag.getDelay(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS));
        }
        return ret;
    }

    /**
     * Queues a delayed request to be sent to as many ids as possible.
     *
     * @param request {@link CDelayedRequest} The request to send.
     */
    synchronized void delayAndQueueRequestForExecution(CDelayedRequest request) {
        request = new CDelayedRequest(request, (long) Math.pow(request.getDelay
                        (TimeUnit
                                .MILLISECONDS),
                EXPONENTIAL_WAIT_INCREASE_FACTOR), TimeUnit.MILLISECONDS);

        if (!mSyncRequestQueue.contains(request)) {
            mSyncRequestQueue.add(request); // Inserts at tail
            onSyncRequestAdded();
        }
    }

    private synchronized void onSyncRequestAdded() {
        final long INTERRUPTED_TAKE_WAIT_MILLIS = 1000L;
        try {
            CDelayedRequest thisRequest = mSyncRequestQueue.poll();
            new Thread(new GCMRequestExecutor(thisRequest)).start();
        } catch (NoSuchElementException e) {
            //Report, take a break and keep on going
            e.printStackTrace(System.err);
            try {
                Thread.sleep(INTERRUPTED_TAKE_WAIT_MILLIS);
            } catch (InterruptedException e1) {
                e.printStackTrace(System.err);
                //Will never happen
            }
            onSyncRequestAdded();
        }
    }

    private class GCMRequestExecutor implements Runnable {
        CDelayedRequest mDelayedRequest;

        private GCMRequestExecutor(CDelayedRequest _request) {
            mDelayedRequest = _request;
        }

        @Override
        public void run() {
            GCMResponseHandlerSingleton.getInstance().handleGCMResponse(mDelayedRequest, HTTPRequestsSingleton
                    .getInstance().performRequest(mDelayedRequest.getPureRequest()));
        }
    }
}