package com.samourai.wallet.home;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.script.Script;

import com.dm.zbar.android.scanner.ZBarConstants;
import com.dm.zbar.android.scanner.ZBarScannerActivity;
import com.samourai.wallet.ExodusActivity;
import com.samourai.wallet.JSONRPC.JSONRPC;
import com.samourai.wallet.JSONRPC.PoW;
import com.samourai.wallet.JSONRPC.TrustedNodeUtil;
import com.samourai.wallet.R;
import com.samourai.wallet.ReceiveActivity;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.SettingsActivity;
import com.samourai.wallet.UTXOActivity;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.api.Tx;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.paynym.ClaimPayNymActivity;
import com.samourai.wallet.cahoots.Cahoots;
import com.samourai.wallet.cahoots.util.CahootsUtil;
import com.samourai.wallet.crypto.AESUtil;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.home.adapters.ItemDividerDecorator;
import com.samourai.wallet.home.adapters.TxAdapter;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.permissions.PermissionsUtil;
import com.samourai.wallet.ricochet.RicochetMeta;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.send.BlockedUTXO;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.SweepUtil;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.service.RefreshService;
import com.samourai.wallet.service.WebSocketService;
import com.samourai.wallet.spend.SendActivity;
import com.samourai.wallet.tx.TxDetailsActivity;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.wallet.util.MessageSignUtil;
import com.samourai.wallet.util.MonetaryUtil;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.PrivKeyReader;
import com.samourai.wallet.util.TimeOutUtil;
import com.samourai.wallet.util.TorUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.bouncycastle.util.encoders.Hex;

import net.i2p.android.ext.floatingactionbutton.FloatingActionButton;
import net.i2p.android.ext.floatingactionbutton.FloatingActionsMenu;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.yanzhenjie.zbar.Symbol;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import info.guardianproject.netcipher.proxy.OrbotHelper;

import static android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE;

public class BalanceActivity extends AppCompatActivity {

    private final static int SCAN_COLD_STORAGE = 2011;
    private final static int SCAN_QR = 2012;
    private static final String TAG = "BalanceActivity";

    private LinearLayout layoutAlert = null;

    private LinearLayout tvBalanceBar = null;
    private TextView tvBalanceAmount = null;
    private TextView tvBalanceUnits = null;

    private ListView txList = null;
    private List<Tx> txs = null;
    private HashMap<String, Boolean> txStates = null;
    private TxAdapter txAdapter = null;
    private SwipeRefreshLayout swipeRefreshLayout = null;

    private FloatingActionsMenu ibQuickSend = null;
    private FloatingActionButton actionReceive = null;
    private FloatingActionButton actionSend = null;
    private FloatingActionButton actionBIP47 = null;
    private RecyclerView TxRecyclerView;
    private ProgressBar progressBar;

    private boolean isBTC = true;
    private BalanceViewModel balanceViewModel;

    private PoWTask powTask = null;
    private RicochetQueueTask ricochetQueueTask = null;
    private ProgressDialog progress = null;
    private com.github.clans.fab.FloatingActionMenu menuFab;
    private SwipeRefreshLayout txSwipeLayout;
    private CollapsingToolbarLayout mCollapsingToolbar;
    public static final String ACTION_INTENT = "com.samourai.wallet.BalanceFragment.REFRESH";
    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {

            if (ACTION_INTENT.equals(intent.getAction())) {

                final boolean notifTx = intent.getBooleanExtra("notifTx", false);
                final boolean fetch = intent.getBooleanExtra("fetch", false);

                final String rbfHash;
                final String blkHash;
                if (intent.hasExtra("rbf")) {
                    rbfHash = intent.getStringExtra("rbf");
                } else {
                    rbfHash = null;
                }
                if (intent.hasExtra("hash")) {
                    blkHash = intent.getStringExtra("hash");
                } else {
                    blkHash = null;
                }

                Handler handler = new Handler();
                handler.post(new Runnable() {
                    public void run() {
                        refreshTx(notifTx, false, false);

                        if (BalanceActivity.this != null) {

                            if (rbfHash != null) {
                                new AlertDialog.Builder(BalanceActivity.this)
                                        .setTitle(R.string.app_name)
                                        .setMessage(rbfHash + "\n\n" + getString(R.string.rbf_incoming))
                                        .setCancelable(true)
                                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {

                                                doExplorerView(rbfHash);

                                            }
                                        })
                                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                ;
                                            }
                                        }).show();

                            }

                        }
                    }
                });

                if (BalanceActivity.this != null && blkHash != null && PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.USE_TRUSTED_NODE, false) == true && TrustedNodeUtil.getInstance().isSet()) {

//                    BalanceActivity.this.runOnUiThread(new Runnable() {
//                    @Override
                    handler.post(new Runnable() {
                        public void run() {
                            if (powTask == null || powTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
                                powTask = new PoWTask();
                                powTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, blkHash);
                            }
                        }

                    });

                }

            }

        }
    };

    public static final String DISPLAY_INTENT = "com.samourai.wallet.BalanceFragment.DISPLAY";
    protected BroadcastReceiver receiverDisplay = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {

            if (DISPLAY_INTENT.equals(intent.getAction())) {

                updateDisplay();

                List<UTXO> utxos = APIFactory.getInstance(BalanceActivity.this).getUtxos(false);
                for (UTXO utxo : utxos) {
                    List<MyTransactionOutPoint> outpoints = utxo.getOutpoints();
                    for (MyTransactionOutPoint out : outpoints) {

                        byte[] scriptBytes = out.getScriptBytes();
                        String address = null;
                        try {
                            if (Bech32Util.getInstance().isBech32Script(Hex.toHexString(scriptBytes))) {
                                address = Bech32Util.getInstance().getAddressFromScript(Hex.toHexString(scriptBytes));
                            } else {
                                address = new Script(scriptBytes).getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                            }
                        } catch (Exception e) {
                            ;
                        }
                        String path = APIFactory.getInstance(BalanceActivity.this).getUnspentPaths().get(address);
                        if (path != null && path.startsWith("M/1/")) {
                            continue;
                        }

                        final String hash = out.getHash().toString();
                        final int idx = out.getTxOutputN();
                        final long amount = out.getValue().longValue();

                        if (amount < BlockedUTXO.BLOCKED_UTXO_THRESHOLD &&
                                !BlockedUTXO.getInstance().contains(hash, idx) &&
                                !BlockedUTXO.getInstance().containsNotDusted(hash, idx)) {

//                            BalanceActivity.this.runOnUiThread(new Runnable() {
//                            @Override
                            Handler handler = new Handler();
                            handler.post(new Runnable() {
                                public void run() {

                                    String message = BalanceActivity.this.getString(R.string.dusting_attempt);
                                    message += "\n\n";
                                    message += BalanceActivity.this.getString(R.string.dusting_attempt_amount);
                                    message += " ";
                                    message += Coin.valueOf(amount).toPlainString();
                                    message += " BTC\n";
                                    message += BalanceActivity.this.getString(R.string.dusting_attempt_id);
                                    message += " ";
                                    message += hash + "-" + idx;

                                    AlertDialog.Builder dlg = new AlertDialog.Builder(BalanceActivity.this)
                                            .setTitle(R.string.dusting_tx)
                                            .setMessage(message)
                                            .setCancelable(false)
                                            .setPositiveButton(R.string.dusting_attempt_mark_unspendable, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int whichButton) {

                                                    BlockedUTXO.getInstance().add(hash, idx, amount);

                                                }
                                            }).setNegativeButton(R.string.dusting_attempt_ignore, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int whichButton) {

                                                    BlockedUTXO.getInstance().addNotDusted(hash, idx);

                                                }
                                            });
                                    if (!isFinishing()) {
                                        dlg.show();
                                    }

                                }
                            });

                        }

                    }

                }

            }

        }
    };

    protected BroadcastReceiver torStatusReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {


            if (OrbotHelper.ACTION_STATUS.equals(intent.getAction())) {

                boolean enabled = (intent.getStringExtra(OrbotHelper.EXTRA_STATUS).equals(OrbotHelper.STATUS_ON));

                TorUtil.getInstance(BalanceActivity.this).setStatusFromBroadcast(enabled);

            }
        }
    };
    private Toolbar toolbar;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance);
        balanceViewModel = ViewModelProviders.of(this).get(BalanceViewModel.class);
        makePaynymAvatarcache();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        TxRecyclerView = findViewById(R.id.rv_txes);
        progressBar = findViewById(R.id.progressBar);
        toolbar = findViewById(R.id.toolbar);
        mCollapsingToolbar = findViewById(R.id.toolbar_layout);
        txSwipeLayout = findViewById(R.id.tx_swipe_container);

        setSupportActionBar(toolbar);
        TxRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        TxRecyclerView.addItemDecoration(new ItemDividerDecorator(getApplicationContext()));
        menuFab = findViewById(R.id.fab_menu);
        progressBar.setVisibility(View.VISIBLE);

        findViewById(R.id.send_fab).setOnClickListener(view -> {
            Intent intent = new Intent(BalanceActivity.this, SendActivity.class);
            intent.putExtra("via_menu", true);
            startActivity(intent);
            menuFab.toggle(true);
        });
        setBalance(0L);
        findViewById(R.id.receive_fab).setOnClickListener(view -> {
            menuFab.toggle(true);

            try {
                HD_Wallet hdw = HD_WalletFactory.getInstance(BalanceActivity.this).get();

                if (hdw != null) {
                    Intent intent = new Intent(BalanceActivity.this, ReceiveActivity.class);
                    startActivity(intent);
                }


            } catch (IOException | MnemonicException.MnemonicLengthException e) {
                ;
            }
        });
        findViewById(R.id.paynym_fab).setOnClickListener(view -> {
            menuFab.toggle(true);
            Intent intent = new Intent(BalanceActivity.this, com.samourai.wallet.bip47.BIP47Activity.class);
            startActivity(intent);
        });
        getSupportActionBar().setIcon(R.drawable.ic_samourai_logo_toolbar);
        txSwipeLayout.setOnRefreshListener(() -> {
            refreshTx(false, true, false);
            txSwipeLayout.setRefreshing(false);
            progressBar.setVisibility(View.VISIBLE);
        });

//        getSupportActionBar().setIcon(R.drawable.ic_samourai_logo_trans2x);
//        LayoutInflater inflator = BalanceActivity.this.getLayoutInflater();
//        tvBalanceBar = (LinearLayout) inflator.inflate(R.layout.balance_layout, null);
//        tvBalanceBar.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if (isBTC) {
//                    isBTC = false;
//                } else {
//                    isBTC = true;
//                }
//                displayBalance();
//                txAdapter.notifyDataSetChanged();
//                return false;
//            }
//        });
//        tvBalanceAmount = (TextView) tvBalanceBar.findViewById(R.id.BalanceAmount);
//        tvBalanceUnits = (TextView) tvBalanceBar.findViewById(R.id.BalanceUnits);
//
//        ibQuickSend = (FloatingActionsMenu) findViewById(R.id.wallet_menu);
//        actionSend = (FloatingActionButton) findViewById(R.id.send);
//        actionSend.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View arg0) {
//
//                Intent intent = new Intent(BalanceActivity.this, SendActivity.class);
//                intent.putExtra("via_menu", true);
//                startActivity(intent);
//
//            }
//        });
//        actionReceive = (FloatingActionButton) findViewById(R.id.receive);
//        actionReceive.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View arg0) {
//
//                try {
//                    HD_Wallet hdw = HD_WalletFactory.getInstance(BalanceActivity.this).get();
//
//                    if (hdw != null) {
//                        Intent intent = new Intent(BalanceActivity.this, ReceiveActivity.class);
//                        startActivity(intent);
//                    }
//
//                } catch (IOException | MnemonicException.MnemonicLengthException e) {
//                    ;
//                }
//
//            }
//        });
//
//        actionBIP47 = (FloatingActionButton) findViewById(R.id.bip47);
//        actionBIP47.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View arg0) {
//                Intent intent = new Intent(BalanceActivity.this, com.samourai.wallet.bip47.BIP47Activity.class);
//                startActivity(intent);
//            }
//        });
//
//        txs = new ArrayList<Tx>();
//        txStates = new HashMap<String, Boolean>();
//        txList = (ListView) findViewById(R.id.txList);
//        txAdapter = new TransactionAdapter();
//        txList.setAdapter(txAdapter);
//        txList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
//
//                if (position == 0) {
//                    return;
//                }
//
//                long viewId = view.getId();
//                View v = (View) view.getParent();
//                final Tx tx = txs.get(position - 1);
//                ImageView ivTxStatus = (ImageView) v.findViewById(R.id.TransactionStatus);
//                TextView tvConfirmationCount = (TextView) v.findViewById(R.id.ConfirmationCount);
//                if (viewId == R.id.ConfirmationCount || viewId == R.id.TransactionStatus) {
//
//                    if (txStates.containsKey(tx.getHash()) && txStates.get(tx.getHash()) == true) {
//                        txStates.put(tx.getHash(), false);
//                        displayTxStatus(false, tx.getConfirmations(), tvConfirmationCount, ivTxStatus);
//                    } else {
//                        txStates.put(tx.getHash(), true);
//                        displayTxStatus(true, tx.getConfirmations(), tvConfirmationCount, ivTxStatus);
//                    }
//
//                } else {
//                    doExplorerView(tx);
//                }
//
//            }
//        });
//
//        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
//        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
//            @Override
//            public void onRefresh() {
//
//                new Handler().post(new Runnable() {
//                    @Override
//                    public void run() {
//                        refreshTx(false, true, false);
//                        swipeRefreshLayout.setRefreshing(false);
//                    }
//                });
//
//            }
//        });
//        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
//                android.R.color.holo_green_light,
//                android.R.color.holo_orange_light,
//                android.R.color.holo_red_light);
//
        IntentFilter filter = new IntentFilter(ACTION_INTENT);
        LocalBroadcastManager.getInstance(BalanceActivity.this).registerReceiver(receiver, filter);
        IntentFilter filterDisplay = new IntentFilter(DISPLAY_INTENT);
        LocalBroadcastManager.getInstance(BalanceActivity.this).registerReceiver(receiverDisplay, filterDisplay);

        TorUtil.getInstance(BalanceActivity.this).setStatusFromBroadcast(false);
        registerReceiver(torStatusReceiver, new IntentFilter(OrbotHelper.ACTION_STATUS));

        if (!PermissionsUtil.getInstance(BalanceActivity.this).hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) || !PermissionsUtil.getInstance(BalanceActivity.this).hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionsUtil.getInstance(BalanceActivity.this).showRequestPermissionsInfoAlertDialog(PermissionsUtil.READ_WRITE_EXTERNAL_PERMISSION_CODE);
        }
        if (!PermissionsUtil.getInstance(BalanceActivity.this).hasPermission(Manifest.permission.CAMERA)) {
            PermissionsUtil.getInstance(BalanceActivity.this).showRequestPermissionsInfoAlertDialog(PermissionsUtil.CAMERA_PERMISSION_CODE);
        }

        if (PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.PAYNYM_CLAIMED, false) == true && PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.PAYNYM_FEATURED_SEGWIT, false) == false) {
            doFeaturePayNymUpdate();
        } else if (PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.PAYNYM_CLAIMED, false) == false &&
                PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.PAYNYM_REFUSED, false) == false) {
            doClaimPayNym();
        } else {
            ;
        }
//
//        if(RicochetMeta.getInstance(BalanceActivity.this).getQueue().size() > 0)    {
//            if (ricochetQueueTask == null || ricochetQueueTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
//                ricochetQueueTask = new RicochetQueueTask();
//                ricochetQueueTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
//            }
//        }
//
//        if (!AppUtil.getInstance(BalanceActivity.this).isClipboardSeen()) {
//            doClipboardCheck();
//        }
//
        final Handler handler = new Handler();
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//
//                progress = new ProgressDialog(BalanceActivity.this);
//                progress.setCancelable(true);
//                progress.setTitle(R.string.app_name);
//                progress.setMessage(getText(R.string.refresh_tx_pre));
//                progress.show();
//            }
//        });
//
        final Handler delayedHandler = new Handler();
        delayedHandler.postDelayed(() -> {

            boolean notifTx = false;
            Bundle extras = getIntent().getExtras();
            if (extras != null && extras.containsKey("notifTx")) {
                notifTx = extras.getBoolean("notifTx");
            }

            refreshTx(notifTx, false, true);

            updateDisplay();
        }, 100L);

        if (!AppUtil.getInstance(BalanceActivity.this.getApplicationContext()).isServiceRunning(WebSocketService.class)) {
            startService(new Intent(BalanceActivity.this.getApplicationContext(), WebSocketService.class));
        }
        initViewModel();
        updateDisplay();
        progressBar.setVisibility(View.VISIBLE);

    }

    private void initViewModel() {
        TxAdapter adapter = new TxAdapter(getApplicationContext(), new ArrayList<>());
        adapter.setHasStableIds(true);
        adapter.setClickListner((position, tx) -> txDetails(tx));

        TxRecyclerView.setAdapter(adapter);
        balanceViewModel.getTxs().observe(this, adapter::setTxes);
        balanceViewModel.getBalance().observe(this, balance -> {
            Log.i(TAG, "initViewModel: ".concat(String.valueOf(balance)));
            setBalance(balance);
        });

        setBalance(balanceViewModel.getBalance().getValue());

    }

    private void setBalance(Long balance) {
        if (balance == null) {
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getBTCDisplayAmount(balance).concat(" ").concat(getBTCDisplayUnits()));
            toolbar.setTitle(getBTCDisplayAmount(balance).concat(" ").concat(getBTCDisplayUnits()));
            setTitle(getBTCDisplayAmount(balance).concat(" ").concat(getBTCDisplayUnits()));
            mCollapsingToolbar.setTitle(getBTCDisplayAmount(balance).concat(" ").concat(getBTCDisplayUnits()));

        }


        Log.i(TAG, "setBalance: ".concat(getBTCDisplayAmount(balance)));

    }

    @Override
    public void onResume() {
        super.onResume();

//        IntentFilter filter = new IntentFilter(ACTION_INTENT);
//        LocalBroadcastManager.getInstance(BalanceActivity.this).registerReceiver(receiver, filter);

        if (TorUtil.getInstance(BalanceActivity.this).statusFromBroadcast()) {
            OrbotHelper.requestStartTor(BalanceActivity.this);
        }

        AppUtil.getInstance(BalanceActivity.this).checkTimeOut();

        Intent intent = new Intent("com.samourai.wallet.MainActivity2.RESTART_SERVICE");
        LocalBroadcastManager.getInstance(BalanceActivity.this).sendBroadcast(intent);

    }

    @Override
    public void onPause() {
        super.onPause();

//        ibQuickSend.collapse();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppUtil.getInstance(BalanceActivity.this.getApplicationContext()).isServiceRunning(WebSocketService.class)) {
            stopService(new Intent(BalanceActivity.this.getApplicationContext(), WebSocketService.class));
        }

    }


    private void makePaynymAvatarcache() {
        try {

            ArrayList<String> paymentCodes = new ArrayList<>(BIP47Meta.getInstance().getSortedByLabels(false, true));
            for (String code : paymentCodes) {
                Picasso.with(getBaseContext())
                        .load(com.samourai.wallet.bip47.paynym.WebUtil.PAYNYM_API + code + "/avatar").fetch(new Callback() {
                    @Override
                    public void onSuccess() {
                        /*NO OP*/
                    }

                    @Override
                    public void onError() {
                        /*NO OP*/
                    }
                });
            }

        } catch (Exception ignored) {

        }
    }

    @Override
    public void onDestroy() {

        LocalBroadcastManager.getInstance(BalanceActivity.this).unregisterReceiver(receiver);
        LocalBroadcastManager.getInstance(BalanceActivity.this).unregisterReceiver(receiverDisplay);

        unregisterReceiver(torStatusReceiver);

        if (AppUtil.getInstance(BalanceActivity.this.getApplicationContext()).isServiceRunning(WebSocketService.class)) {
            stopService(new Intent(BalanceActivity.this.getApplicationContext(), WebSocketService.class));
        }

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!OrbotHelper.isOrbotInstalled(BalanceActivity.this)) {
            menu.findItem(R.id.action_tor).setVisible(false);
        } else if (TorUtil.getInstance(BalanceActivity.this).statusFromBroadcast()) {
            OrbotHelper.requestStartTor(BalanceActivity.this);
            menu.findItem(R.id.action_tor).setIcon(R.drawable.tor_on);
        } else {
            menu.findItem(R.id.action_tor).setIcon(R.drawable.tor_off);
        }
        menu.findItem(R.id.action_refresh).setVisible(false);
        menu.findItem(R.id.action_share_receive).setVisible(false);
        menu.findItem(R.id.action_ricochet).setVisible(false);
        menu.findItem(R.id.action_empty_ricochet).setVisible(false);
        menu.findItem(R.id.action_sign).setVisible(false);
        menu.findItem(R.id.action_fees).setVisible(false);
        menu.findItem(R.id.action_batch).setVisible(false);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            doSettings();
        } else if (id == R.id.action_support) {
            doSupport();
        } else if (id == R.id.action_sweep) {
            if (!AppUtil.getInstance(BalanceActivity.this).isOfflineMode()) {
                doSweep();
            } else {
                Toast.makeText(BalanceActivity.this, R.string.in_offline_mode, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.action_utxo) {
            doUTXO();
        } else if (id == R.id.action_tor) {

            if (!OrbotHelper.isOrbotInstalled(BalanceActivity.this)) {
                ;
            } else if (TorUtil.getInstance(BalanceActivity.this).statusFromBroadcast()) {
                item.setIcon(R.drawable.tor_off);
                TorUtil.getInstance(BalanceActivity.this).setStatusFromBroadcast(false);
            } else {
                OrbotHelper.requestStartTor(BalanceActivity.this);
                item.setIcon(R.drawable.tor_on);
                TorUtil.getInstance(BalanceActivity.this).setStatusFromBroadcast(true);
            }

            return true;

        } else if (id == R.id.action_backup) {

            if (SamouraiWallet.getInstance().hasPassphrase(BalanceActivity.this)) {
                try {
                    if (HD_WalletFactory.getInstance(BalanceActivity.this).get() != null && SamouraiWallet.getInstance().hasPassphrase(BalanceActivity.this)) {
                        doBackup();
                    } else {

                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setMessage(R.string.passphrase_needed_for_backup).setCancelable(false);
                        AlertDialog alert = builder.create();

                        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        });

                        if (!isFinishing()) {
                            alert.show();
                        }

                    }
                } catch (MnemonicException.MnemonicLengthException mle) {
                    ;
                } catch (IOException ioe) {
                    ;
                }
            } else {
                Toast.makeText(BalanceActivity.this, R.string.passphrase_required, Toast.LENGTH_SHORT).show();
            }

        } else if (id == R.id.action_scan_qr) {
            doScan();
        } else {
            ;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK && requestCode == SCAN_COLD_STORAGE) {

            if (data != null && data.getStringExtra(ZBarConstants.SCAN_RESULT) != null) {

                final String strResult = data.getStringExtra(ZBarConstants.SCAN_RESULT);

                doPrivKey(strResult);

            }
        } else if (resultCode == Activity.RESULT_CANCELED && requestCode == SCAN_COLD_STORAGE) {
            ;
        } else if (resultCode == Activity.RESULT_OK && requestCode == SCAN_QR) {

            if (data != null && data.getStringExtra(ZBarConstants.SCAN_RESULT) != null) {

                final String strResult = data.getStringExtra(ZBarConstants.SCAN_RESULT);

                PrivKeyReader privKeyReader = new PrivKeyReader(new CharSequenceX(strResult.trim()));
                try {
                    if (privKeyReader.getFormat() != null) {
                        doPrivKey(strResult.trim());
                    } else if (Cahoots.isCahoots(strResult.trim())) {
                        CahootsUtil.getInstance(BalanceActivity.this).processCahoots(strResult.trim());
                    } else if (FormatsUtil.getInstance().isPSBT(strResult.trim())) {
                        CahootsUtil.getInstance(BalanceActivity.this).doPSBT(strResult.trim());
                    } else {
                        Intent intent = new Intent(BalanceActivity.this, SendActivity.class);
                        intent.putExtra("uri", strResult.trim());
                        startActivity(intent);
                    }
                } catch (Exception e) {
                    ;
                }

            }
        } else if (resultCode == Activity.RESULT_CANCELED && requestCode == SCAN_QR) {
            ;
        } else {
            ;
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.ask_you_sure_exit).setCancelable(false);
            AlertDialog alert = builder.create();

            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                    try {
                        PayloadUtil.getInstance(BalanceActivity.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(BalanceActivity.this).getGUID() + AccessFactory.getInstance(BalanceActivity.this).getPIN()));
                    } catch (MnemonicException.MnemonicLengthException mle) {
                        ;
                    } catch (JSONException je) {
                        ;
                    } catch (IOException ioe) {
                        ;
                    } catch (DecryptionException de) {
                        ;
                    }

                    Intent intent = new Intent(BalanceActivity.this, ExodusActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    BalanceActivity.this.startActivity(intent);

                }
            });

            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });

            if (!isFinishing()) {
                alert.show();
            }

            return true;
        } else {
            ;
        }

        return false;
    }

    private void updateDisplay() {
        txs = APIFactory.getInstance(BalanceActivity.this).getAllXpubTxs();
        Log.i(TAG, "updateDisplay: ".concat(String.valueOf(txs.size())));
        balanceViewModel.setTx(txs);
        long balance = 0L;

        try {
            balance = APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(0).xpubstr());
        } catch (IOException ioe) {
            ;
        } catch (MnemonicException.MnemonicLengthException mle) {
            ;
        } catch (NullPointerException npe) {
            ;
        }

        balanceViewModel.setBalance(balance);
        if (progressBar.getVisibility() == View.VISIBLE) {
            progressBar.setVisibility(View.INVISIBLE);
        }
//        if (txs != null) {
//            Collections.sort(txs, new APIFactory.TxMostRecentDateComparator());
//        }
//        tvBalanceAmount.setText("");
//        tvBalanceUnits.setText("");
//        displayBalance();
////        txAdapter.notifyDataSetChanged();
//
//        if (progress != null && progress.isShowing()) {
//            progress.dismiss();
//            progress.cancel();
//            progress = null;
//        }

    }

    private void doClaimPayNym() {
        Intent intent = new Intent(BalanceActivity.this, ClaimPayNymActivity.class);
        startActivity(intent);
    }

    private void doSettings() {
        TimeOutUtil.getInstance().updatePin();
        Intent intent = new Intent(BalanceActivity.this, SettingsActivity.class);
        startActivity(intent);
    }

    private void doSupport() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://samourai.kayako.com/"));
        startActivity(intent);
    }

    private void doUTXO() {
        Intent intent = new Intent(BalanceActivity.this, UTXOActivity.class);
        startActivity(intent);
    }

    private void doScan() {
        Intent intent = new Intent(BalanceActivity.this, ZBarScannerActivity.class);
        intent.putExtra(ZBarConstants.SCAN_MODES, new int[]{Symbol.QRCODE});
        startActivityForResult(intent, SCAN_QR);
    }

    private void doSweepViaScan() {
        Intent intent = new Intent(BalanceActivity.this, ZBarScannerActivity.class);
        intent.putExtra(ZBarConstants.SCAN_MODES, new int[]{Symbol.QRCODE});
        startActivityForResult(intent, SCAN_COLD_STORAGE);
    }

    private void doSweep() {

        AlertDialog.Builder dlg = new AlertDialog.Builder(BalanceActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.action_sweep)
                .setCancelable(true)
                .setPositiveButton(R.string.enter_privkey, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        final EditText privkey = new EditText(BalanceActivity.this);
                        privkey.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

                        AlertDialog.Builder dlg = new AlertDialog.Builder(BalanceActivity.this)
                                .setTitle(R.string.app_name)
                                .setMessage(R.string.enter_privkey)
                                .setView(privkey)
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        final String strPrivKey = privkey.getText().toString();

                                        if (strPrivKey != null && strPrivKey.length() > 0) {
                                            doPrivKey(strPrivKey);
                                        }

                                    }
                                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {

                                        dialog.dismiss();

                                    }
                                });
                        if (!isFinishing()) {
                            dlg.show();
                        }

                    }

                }).setNegativeButton(R.string.scan, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        doSweepViaScan();

                    }
                });
        if (!isFinishing()) {
            dlg.show();
        }

    }

    private void doPrivKey(final String data) {

        PrivKeyReader privKeyReader = null;

        String format = null;
        try {
            privKeyReader = new PrivKeyReader(new CharSequenceX(data), null);
            format = privKeyReader.getFormat();
        } catch (Exception e) {
            Toast.makeText(BalanceActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (format != null) {

            if (format.equals(PrivKeyReader.BIP38)) {

                final PrivKeyReader pvr = privKeyReader;

                final EditText password38 = new EditText(BalanceActivity.this);
                password38.setSingleLine(true);
                password38.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

                AlertDialog.Builder dlg = new AlertDialog.Builder(BalanceActivity.this)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.bip38_pw)
                        .setView(password38)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                String password = password38.getText().toString();

                                ProgressDialog progress = new ProgressDialog(BalanceActivity.this);
                                progress.setCancelable(false);
                                progress.setTitle(R.string.app_name);
                                progress.setMessage(getString(R.string.decrypting_bip38));
                                progress.show();

                                boolean keyDecoded = false;

                                try {
                                    BIP38PrivateKey bip38 = new BIP38PrivateKey(SamouraiWallet.getInstance().getCurrentNetworkParams(), data);
                                    final ECKey ecKey = bip38.decrypt(password);
                                    if (ecKey != null && ecKey.hasPrivKey()) {

                                        if (progress != null && progress.isShowing()) {
                                            progress.cancel();
                                        }

                                        pvr.setPassword(new CharSequenceX(password));
                                        keyDecoded = true;

                                        Toast.makeText(BalanceActivity.this, pvr.getFormat(), Toast.LENGTH_SHORT).show();
                                        Toast.makeText(BalanceActivity.this, pvr.getKey().toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString(), Toast.LENGTH_SHORT).show();

                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Toast.makeText(BalanceActivity.this, R.string.bip38_pw_error, Toast.LENGTH_SHORT).show();
                                }

                                if (progress != null && progress.isShowing()) {
                                    progress.cancel();
                                }

                                if (keyDecoded) {
                                    SweepUtil.getInstance(BalanceActivity.this).sweep(pvr, SweepUtil.TYPE_P2PKH);
                                }

                            }
                        }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                Toast.makeText(BalanceActivity.this, R.string.bip38_pw_error, Toast.LENGTH_SHORT).show();

                            }
                        });
                if (!isFinishing()) {
                    dlg.show();
                }

            } else if (privKeyReader != null) {
                SweepUtil.getInstance(BalanceActivity.this).sweep(privKeyReader, SweepUtil.TYPE_P2PKH);
            } else {
                ;
            }

        } else {
            Toast.makeText(BalanceActivity.this, R.string.cannot_recognize_privkey, Toast.LENGTH_SHORT).show();
        }

    }

    private void doBackup() {

        try {
            final String passphrase = HD_WalletFactory.getInstance(BalanceActivity.this).get().getPassphrase();

            final String[] export_methods = new String[2];
            export_methods[0] = getString(R.string.export_to_clipboard);
            export_methods[1] = getString(R.string.export_to_email);

            new AlertDialog.Builder(BalanceActivity.this)
                    .setTitle(R.string.options_export)
                    .setSingleChoiceItems(export_methods, 0, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {

                                    try {
                                        PayloadUtil.getInstance(BalanceActivity.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(BalanceActivity.this).getGUID() + AccessFactory.getInstance(BalanceActivity.this).getPIN()));
                                    } catch (IOException ioe) {
                                        ;
                                    } catch (JSONException je) {
                                        ;
                                    } catch (DecryptionException de) {
                                        ;
                                    } catch (MnemonicException.MnemonicLengthException mle) {
                                        ;
                                    }

                                    String encrypted = null;
                                    try {
                                        encrypted = AESUtil.encrypt(PayloadUtil.getInstance(BalanceActivity.this).getPayload().toString(), new CharSequenceX(passphrase), AESUtil.DefaultPBKDF2Iterations);
                                    } catch (Exception e) {
                                        Toast.makeText(BalanceActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                    } finally {
                                        if (encrypted == null) {
                                            Toast.makeText(BalanceActivity.this, R.string.encryption_error, Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                    }

                                    JSONObject obj = PayloadUtil.getInstance(BalanceActivity.this).putPayload(encrypted, true);

                                    if (which == 0) {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = null;
                                        clip = android.content.ClipData.newPlainText("Wallet backup", obj.toString());
                                        clipboard.setPrimaryClip(clip);
                                        Toast.makeText(BalanceActivity.this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Intent email = new Intent(Intent.ACTION_SEND);
                                        email.putExtra(Intent.EXTRA_SUBJECT, "Samourai Wallet backup");
                                        email.putExtra(Intent.EXTRA_TEXT, obj.toString());
                                        email.setType("message/rfc822");
                                        startActivity(Intent.createChooser(email, BalanceActivity.this.getText(R.string.choose_email_client)));
                                    }

                                    dialog.dismiss();
                                }
                            }
                    ).show();

        } catch (IOException ioe) {
            ioe.printStackTrace();
            Toast.makeText(BalanceActivity.this, "HD wallet error", Toast.LENGTH_SHORT).show();
        } catch (MnemonicException.MnemonicLengthException mle) {
            mle.printStackTrace();
            Toast.makeText(BalanceActivity.this, "HD wallet error", Toast.LENGTH_SHORT).show();
        }

    }

    private void doClipboardCheck() {

        final android.content.ClipboardManager clipboard = (android.content.ClipboardManager) BalanceActivity.this.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            final ClipData clip = clipboard.getPrimaryClip();
            ClipData.Item item = clip.getItemAt(0);
            if (item.getText() != null) {
                String text = item.getText().toString();
                String[] s = text.split("\\s+");

                try {
                    for (int i = 0; i < s.length; i++) {
                        PrivKeyReader privKeyReader = new PrivKeyReader(new CharSequenceX(s[i]));
                        if (privKeyReader.getFormat() != null &&
                                (privKeyReader.getFormat().equals(PrivKeyReader.WIF_COMPRESSED) ||
                                        privKeyReader.getFormat().equals(PrivKeyReader.WIF_UNCOMPRESSED) ||
                                        privKeyReader.getFormat().equals(PrivKeyReader.BIP38) ||
                                        FormatsUtil.getInstance().isValidXprv(s[i])
                                )
                        ) {

                            new AlertDialog.Builder(BalanceActivity.this)
                                    .setTitle(R.string.app_name)
                                    .setMessage(R.string.privkey_clipboard)
                                    .setCancelable(false)
                                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                                        public void onClick(DialogInterface dialog, int whichButton) {

                                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""));

                                        }

                                    }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int whichButton) {
                                    ;
                                }
                            }).show();

                        }
                    }
                } catch (Exception e) {
                    ;
                }
            }
        }

    }


    private void refreshTx(final boolean notifTx, final boolean dragged, final boolean launch) {

        if (AppUtil.getInstance(BalanceActivity.this).isOfflineMode()) {
            Toast.makeText(BalanceActivity.this, R.string.in_offline_mode, Toast.LENGTH_SHORT).show();
            /*
            CoordinatorLayout coordinatorLayout = new CoordinatorLayout(BalanceActivity.this);
            Snackbar snackbar = Snackbar.make(coordinatorLayout, R.string.in_offline_mode, Snackbar.LENGTH_LONG);
            snackbar.show();
            */
        }

        Intent intent = new Intent(BalanceActivity.this, RefreshService.class);
        intent.putExtra("notifTx", notifTx);
        intent.putExtra("dragged", dragged);
        intent.putExtra("launch", launch);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

    }

    private void displayBalance() {

        long balance = 0L;

        try {
            balance = APIFactory.getInstance(BalanceActivity.this).getXpubAmounts().get(HD_WalletFactory.getInstance(BalanceActivity.this).get().getAccount(0).xpubstr());
        } catch (IOException ioe) {
            ;
        } catch (MnemonicException.MnemonicLengthException mle) {
            ;
        } catch (NullPointerException npe) {
            ;
        }

        if (isBTC) {
            tvBalanceAmount.setText(getBTCDisplayAmount(balance));
            tvBalanceUnits.setText(getBTCDisplayUnits());
        } else {
            tvBalanceAmount.setText(getSatoshiDisplayAmount(balance));
            tvBalanceUnits.setText(getSatoshiDisplayUnits());
        }

    }

    private String getBTCDisplayAmount(long value) {
        return Coin.valueOf(value).toPlainString();
    }

    private String getSatoshiDisplayAmount(long value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#", symbols);
        df.setMinimumIntegerDigits(1);
        df.setMaximumIntegerDigits(16);
        df.setGroupingUsed(true);
        df.setGroupingSize(3);
        return df.format(value);
    }

    private String getBTCDisplayUnits() {

        return MonetaryUtil.getInstance().getBTCUnits();

    }

    private String getSatoshiDisplayUnits() {

        return MonetaryUtil.getInstance().getSatoshiUnits();

    }

    private void displayTxStatus(boolean heads, long confirmations, TextView tvConfirmationCount, ImageView ivTxStatus) {

        if (heads) {
            if (confirmations == 0) {
                rotateTxStatus(tvConfirmationCount, true);
                ivTxStatus.setVisibility(View.VISIBLE);
                ivTxStatus.setImageResource(R.drawable.ic_query_builder_white);
                tvConfirmationCount.setVisibility(View.GONE);
            } else if (confirmations > 3) {
                rotateTxStatus(tvConfirmationCount, true);
                ivTxStatus.setVisibility(View.VISIBLE);
                ivTxStatus.setImageResource(R.drawable.ic_done_white);
                tvConfirmationCount.setVisibility(View.GONE);
            } else {
                rotateTxStatus(ivTxStatus, false);
                tvConfirmationCount.setVisibility(View.VISIBLE);
                tvConfirmationCount.setText(Long.toString(confirmations));
                ivTxStatus.setVisibility(View.GONE);
            }
        } else {
            if (confirmations < 100) {
                rotateTxStatus(ivTxStatus, false);
                tvConfirmationCount.setVisibility(View.VISIBLE);
                tvConfirmationCount.setText(Long.toString(confirmations));
                ivTxStatus.setVisibility(View.GONE);
            } else {
                rotateTxStatus(ivTxStatus, false);
                tvConfirmationCount.setVisibility(View.VISIBLE);
                tvConfirmationCount.setText("\u221e");
                ivTxStatus.setVisibility(View.GONE);
            }
        }

    }

    private void rotateTxStatus(View view, boolean clockwise) {

        float degrees = 360f;
        if (!clockwise) {
            degrees = -360f;
        }

        ObjectAnimator animation = ObjectAnimator.ofFloat(view, "rotationY", 0.0f, degrees);
        animation.setDuration(1000);
        animation.setRepeatCount(0);
        animation.setInterpolator(new AnticipateInterpolator());
        animation.start();
    }

    private void doExplorerView(String strHash) {

        if (strHash != null) {

            String blockExplorer = "https://m.oxt.me/transaction/";
            if (SamouraiWallet.getInstance().isTestNet()) {
                blockExplorer = "https://blockstream.info/testnet/";
            }
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(blockExplorer + strHash));
            startActivity(browserIntent);
        }

    }

    private void txDetails(Tx tx) {
//
//        if(strHash != null) {
//            int sel = PrefsUtil.getInstance(BalanceActivity.this).getValue(PrefsUtil.BLOCK_EXPLORER, 0);
//            if(sel >= BlockExplorerUtil.getInstance().getBlockExplorerTxUrls().length)    {
//                sel = 0;
//            }
//            CharSequence url = BlockExplorerUtil.getInstance().getBlockExplorerTxUrls()[sel];

        Intent txIntent = new Intent(this, TxDetailsActivity.class);
        txIntent.putExtra("TX", tx.toJSON().toString());
        startActivity(txIntent);
    }

    private class RicochetQueueTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {

            if (RicochetMeta.getInstance(BalanceActivity.this).getQueue().size() > 0) {

                int count = 0;

                final Iterator<JSONObject> itr = RicochetMeta.getInstance(BalanceActivity.this).getIterator();

                while (itr.hasNext()) {

                    if (count == 3) {
                        break;
                    }

                    try {
                        JSONObject jObj = itr.next();
                        JSONArray jHops = jObj.getJSONArray("hops");
                        if (jHops.length() > 0) {

                            JSONObject jHop = jHops.getJSONObject(jHops.length() - 1);
                            String txHash = jHop.getString("hash");

                            JSONObject txObj = APIFactory.getInstance(BalanceActivity.this).getTxInfo(txHash);
                            if (txObj != null && txObj.has("block_height") && txObj.getInt("block_height") != -1) {
                                itr.remove();
                                count++;
                            }

                        }
                    } catch (JSONException je) {
                        ;
                    }
                }

            }

            if (RicochetMeta.getInstance(BalanceActivity.this).getStaggered().size() > 0) {

                int count = 0;

                List<JSONObject> staggered = RicochetMeta.getInstance(BalanceActivity.this).getStaggered();
                List<JSONObject> _staggered = new ArrayList<JSONObject>();

                for (JSONObject jObj : staggered) {

                    if (count == 3) {
                        break;
                    }

                    try {
                        JSONArray jHops = jObj.getJSONArray("script");
                        if (jHops.length() > 0) {

                            JSONObject jHop = jHops.getJSONObject(jHops.length() - 1);
                            String txHash = jHop.getString("tx");

                            JSONObject txObj = APIFactory.getInstance(BalanceActivity.this).getTxInfo(txHash);
                            if (txObj != null && txObj.has("block_height") && txObj.getInt("block_height") != -1) {
                                count++;
                            } else {
                                _staggered.add(jObj);
                            }

                        }
                    } catch (JSONException je) {
                        ;
                    } catch (ConcurrentModificationException cme) {
                        ;
                    }
                }

            }

            return "OK";
        }

        @Override
        protected void onPostExecute(String result) {
            ;
        }

        @Override
        protected void onPreExecute() {
            ;
        }

    }

    private class PoWTask extends AsyncTask<String, Void, String> {

        private boolean isOK = true;
        private String strBlockHash = null;

        @Override
        protected String doInBackground(String... params) {

            strBlockHash = params[0];

            JSONRPC jsonrpc = new JSONRPC(TrustedNodeUtil.getInstance().getUser(), TrustedNodeUtil.getInstance().getPassword(), TrustedNodeUtil.getInstance().getNode(), TrustedNodeUtil.getInstance().getPort());
            JSONObject nodeObj = jsonrpc.getBlockHeader(strBlockHash);
            if (nodeObj != null && nodeObj.has("hash")) {
                PoW pow = new PoW(strBlockHash);
                String hash = pow.calcHash(nodeObj);
                if (hash != null && hash.toLowerCase().equals(strBlockHash.toLowerCase())) {

                    JSONObject headerObj = APIFactory.getInstance(BalanceActivity.this).getBlockHeader(strBlockHash);
                    if (headerObj != null && headerObj.has("")) {
                        if (!pow.check(headerObj, nodeObj, hash)) {
                            isOK = false;
                        }
                    }

                } else {
                    isOK = false;
                }
            }

            return "OK";
        }

        @Override
        protected void onPostExecute(String result) {

            if (!isOK) {

                new AlertDialog.Builder(BalanceActivity.this)
                        .setTitle(R.string.app_name)
                        .setMessage(getString(R.string.trusted_node_pow_failed) + "\n" + "Block hash:" + strBlockHash)
                        .setCancelable(false)
                        .setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                dialog.dismiss();

                            }
                        }).show();

            }

        }

        @Override
        protected void onPreExecute() {
            ;
        }

    }

    private void doFeaturePayNymUpdate() {

        new Thread(new Runnable() {

            private Handler handler = new Handler();

            @Override
            public void run() {

                Looper.prepare();

                try {

                    JSONObject obj = new JSONObject();
                    obj.put("code", BIP47Util.getInstance(BalanceActivity.this).getPaymentCode().toString());
//                    Log.d("BalanceActivity", obj.toString());
                    String res = com.samourai.wallet.bip47.paynym.WebUtil.getInstance(BalanceActivity.this).postURL("application/json", null, com.samourai.wallet.bip47.paynym.WebUtil.PAYNYM_API + "api/v1/token", obj.toString());
//                    Log.d("BalanceActivity", res);

                    JSONObject responseObj = new JSONObject(res);
                    if (responseObj.has("token")) {
                        String token = responseObj.getString("token");

                        String sig = MessageSignUtil.getInstance(BalanceActivity.this).signMessage(BIP47Util.getInstance(BalanceActivity.this).getNotificationAddress().getECKey(), token);
//                        Log.d("BalanceActivity", sig);

                        obj = new JSONObject();
                        obj.put("nym", BIP47Util.getInstance(BalanceActivity.this).getPaymentCode().toString());
                        obj.put("code", BIP47Util.getInstance(BalanceActivity.this).getFeaturePaymentCode().toString());
                        obj.put("signature", sig);

//                        Log.d("BalanceActivity", "nym/add:" + obj.toString());
                        res = com.samourai.wallet.bip47.paynym.WebUtil.getInstance(BalanceActivity.this).postURL("application/json", token, com.samourai.wallet.bip47.paynym.WebUtil.PAYNYM_API + "api/v1/nym/add", obj.toString());
//                        Log.d("BalanceActivity", res);

                        responseObj = new JSONObject(res);
                        if (responseObj.has("segwit") && responseObj.has("token")) {
                            PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.PAYNYM_FEATURED_SEGWIT, true);
                        } else if (responseObj.has("claimed") && responseObj.getBoolean("claimed") == true) {
                            PrefsUtil.getInstance(BalanceActivity.this).setValue(PrefsUtil.PAYNYM_FEATURED_SEGWIT, true);
                        } else {
                            ;
                        }

                    } else {
                        ;
                    }

                } catch (JSONException je) {
                    je.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Looper.loop();

            }

        }).start();

    }

}