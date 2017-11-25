package vn.com.wespeak.wespeak.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import vn.com.wespeak.wespeak.Constant;
import vn.com.wespeak.wespeak.Logger;
import vn.com.wespeak.wespeak.R;
import vn.com.wespeak.wespeak.StatusBarUtil;
import vn.com.wespeak.wespeak.Utils;
import vn.com.wespeak.wespeak.dialog.DialogEndCalling;
import vn.com.wespeak.wespeak.dialog.DialogStartCalling;
import vn.com.wespeak.wespeak.model.User;
import vn.com.wespeak.wespeak.opentok.OpenTokConfig;
import vn.com.wespeak.wespeak.permission.PermissionHelper;

public class CallingActivity extends AppCompatActivity implements Session.SessionListener,
        PublisherKit.PublisherListener,
        SubscriberKit.SubscriberListener, DialogEndCalling.OnDialogEndCall, DialogStartCalling.onDialogStartCallingCallBack {

    @BindView(R.id.subscriber_container)
    FrameLayout mSubscriberContainer;
    @BindView(R.id.publisher_container)
    FrameLayout mPublisherContainer;
    @BindView(R.id.tv_user_partner)
    TextView mTvUserPartner;
    @BindView(R.id.tv_timer)
    TextView mTvTimer;
    @BindView(R.id.progress)
    ProgressBar mProgress;
    @BindView(R.id.contain_interact)
    LinearLayout mContainInteract;

    private Session mSession;
    private Subscriber mSubscriber;

    private PermissionHelper mPermission;
    private long start;
    private User userAnother;
    private int type;
    private String convesationId;
    private Handler timer = new Handler();
    private Runnable timerRunable = new Runnable() {
        @Override
        public void run() {
            if (mTvTimer != null) {
                long result = System.currentTimeMillis() - start;
                mTvTimer.setText(Utils.makeShortTimeString(CallingActivity.this, result));
            }
        }
    };
    private boolean isStart;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calling);
        ButterKnife.bind(this);
        StatusBarUtil.setColorNoTranslucent(this, ContextCompat.getColor(this, R.color.black));

        mPermission = new PermissionHelper(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermission();
    }

    private void checkPermission() {
        if (mPermission.checkPermission())
            displayCalling();
        else
            mPermission.requestAllPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mPermission.onRequestPermissionsResult(requestCode, grantResults)) {
            displayCalling();
        } else {
            checkPermission();
        }
    }

    private void displayCalling() {

        if (getIntent().getExtras() != null) {
            String userMain = getIntent().getExtras().getString(Constant.EXTRA.USER_MAIN);
            userAnother = getIntent().getExtras().getParcelable(Constant.EXTRA.USER_ANOTHER);
            type = getIntent().getExtras().getInt(Constant.EXTRA.CALL_TYPE);
            convesationId = getIntent().getExtras().getString(Constant.EXTRA.CONVERSATION_ID);
            //display dialog
            if (userMain != null && userAnother != null) {
                if (type != Constant.CallType.RECEIVER) {
                    DialogStartCalling dialogStartCalling = DialogStartCalling.getInstance(userAnother);
                    dialogStartCalling.setOnDialogStartCallingCallBack(this);
                    dialogStartCalling.show(getSupportFragmentManager(), "Calling Dialog Start");
                }
                // if there is no server URL set
                // use hard coded session values
                if (OpenTokConfig.areHardCodedConfigsValid()) {
                    initializeSession(OpenTokConfig.API_KEY, OpenTokConfig.SESSION_ID, userMain);
                } else {
                    showConfigError(OpenTokConfig.hardCodedConfigErrorMessage);
                }
            }
        } else
            finish();
    }

    @Override
    protected void onPause() {

        Logger.debug("onPause");

        super.onPause();

        if (mSession != null) {
            mSession.onPause();
        }

        if (isStart)
            timer.removeCallbacks(timerRunable);
    }

    @Override
    protected void onResume() {

        Logger.debug("onResume");

        super.onResume();

        if (mSession != null) {
            mSession.onResume();
        }

        if (isStart) {
            timer.postDelayed(timerRunable, 1000);
        }
    }

    private void initializeSession(String apiKey, String sessionId, String token) {
        mSession = new Session.Builder(this, apiKey, sessionId).build();
        mSession.setSessionListener(this);
        mSession.connect(token);
    }

    /* Session Listener methods */

    @Override
    public void onConnected(Session session) {

        Logger.debug("Open tok", "onConnected: Connected to session: " + session.getSessionId());

        // initialize Publisher and set this object to listen to Publisher events
        Publisher mPublisher = new Publisher.Builder(this).build();
        mPublisher.setPublisherListener(this);

        // set publisher icon_unvideo style to fill view
        mPublisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);
        mPublisherContainer.addView(mPublisher.getView());
        if (mPublisher.getView() instanceof GLSurfaceView) {
            ((GLSurfaceView) mPublisher.getView()).setZOrderOnTop(true);
        }

        mSession.publish(mPublisher);
    }

    @Override
    public void onDisconnected(Session session) {

        Logger.debug("Open tok", "Open tok", "onDisconnected: Disconnected from session: " + session.getSessionId());
        //todo :go to activity rating
        goToRating();
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {

        Logger.debug("Open tok", "onStreamReceived: New Stream Received " + stream.getStreamId() + " in session: " + session.getSessionId());


        mProgress.setVisibility(View.GONE);
        if (mSubscriber == null) {
            mSubscriber = new Subscriber.Builder(this, stream).build();
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSubscriber.setSubscriberListener(this);
            mSession.subscribe(mSubscriber);
            mSubscriberContainer.addView(mSubscriber.getView());
        }
        start = System.currentTimeMillis();
        isStart = true;
        timer.post(timerRunable);
        mContainInteract.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {

        Logger.debug("Open tok", "onStreamDropped: Stream Dropped: " + stream.getStreamId() + " in session: " + session.getSessionId());

        if (mSubscriber != null) {
            mSubscriber = null;
            mSubscriberContainer.removeAllViews();
        }

        // TODO: go to activity rating
        if (mSession != null) {
            mSession.disconnect();
        }
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Logger.debug("Open tok", "onError: " + opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() + " - " + opentokError.getMessage() + " in session: " + session.getSessionId());

        showOpenTokError(opentokError);
    }

    /* Publisher Listener methods */

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {

        Logger.debug("Open tok", "onStreamCreated: Publisher Stream Created. Own stream " + stream.getStreamId());

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

        Logger.debug("Open tok", "onStreamDestroyed: Publisher Stream Destroyed. Own stream " + stream.getStreamId());
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {

        Logger.debug("Open tok", "onError: " + opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() + " - " + opentokError.getMessage());

        showOpenTokError(opentokError);
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {

        Logger.debug("Open tok", "onConnected: Subscriber connected. Stream: " + subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {

        Logger.debug("Open tok", "onDisconnected: Subscriber disconnected. Stream: " + subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {

        Logger.debug("Open tok", "onError: " + opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() + " - " + opentokError.getMessage());

        showOpenTokError(opentokError);
    }

    private void showOpenTokError(OpentokError opentokError) {

        Toast.makeText(this, opentokError.getErrorDomain().name() + ": " + opentokError.getMessage() + " Please, see the logcat.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void showConfigError(final String errorMessage) {
        Logger.debug("Error " + "Configuration Error" + ": " + errorMessage);
        new AlertDialog.Builder(this)
                .setTitle("Configuration Error")
                .setMessage(errorMessage)
                .setPositiveButton("ok", (dialog, which) -> CallingActivity.this.finish())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @OnClick({R.id.iv_end_call, R.id.iv_unvideo, R.id.iv_mute})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.iv_end_call:
                showDialogExit();
                break;
            case R.id.iv_unvideo:
                if (mSubscriber != null) {
                    if (mSubscriber.getSubscribeToVideo())
                        mSubscriber.setSubscribeToVideo(false);
                    else
                        mSubscriber.setSubscribeToVideo(true);
                }
                break;
            case R.id.iv_mute:
                if (mSubscriber != null) {
                    if (mSubscriber.getSubscribeToAudio())
                        mSubscriber.setSubscribeToAudio(false);
                    else
                        mSubscriber.setSubscribeToAudio(true);
                }
                break;
        }
    }

    private void showDialogExit() {
        DialogEndCalling dialog = DialogEndCalling.getInstance();
        dialog.setOnDialogEndCall(this);
        dialog.show(getSupportFragmentManager(), "Calling Dialog End");
    }

    @Override
    public void onEnd() {
        if (mSession != null) {
            mSession.disconnect();
        }
    }

    @Override
    public void onBackPressed() {
        showDialogExit();
    }

    private void goToRating() {
        Intent ratingActivity = new Intent(CallingActivity.this, RatingActivity.class);
        if (userAnother != null){
            ratingActivity.putExtra(Constant.EXTRA.USER_RATING_TYPE,type);
            ratingActivity.putExtra(Constant.EXTRA.USER_RATING,userAnother.rating);
            ratingActivity.putExtra(Constant.EXTRA.CONVERSATION_ID_RATING,convesationId);
        }

        startActivity(ratingActivity);
        finish();
    }

    @Override
    public void onDialogStartListener(DialogStartCalling dialog, User user) {
        if (dialog != null)
            dialog.dismiss();
    }
}
