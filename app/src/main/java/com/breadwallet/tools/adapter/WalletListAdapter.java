package com.breadwallet.tools.adapter;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.breadwallet.R;
import com.breadwallet.core.BRCorePeer;
import com.breadwallet.presenter.customviews.BRText;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.threads.BRExecutor;
import com.breadwallet.tools.util.BRConnectivityStatus;
import com.breadwallet.tools.util.CurrencyUtils;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.wallets.bitcoin.WalletBitcoinManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by byfieldj on 1/31/18.
 */

public class WalletListAdapter extends RecyclerView.Adapter<WalletListAdapter.WalletItemViewHolder> {

    public static final String TAG = WalletListAdapter.class.getName();

    private final Context mContext;
    private ArrayList<BaseWalletManager> mWalletList;

    private ArrayList<Pair<WalletItemViewHolder, Boolean>> mSyncList = new ArrayList<>();


    public WalletListAdapter(Context context, ArrayList<BaseWalletManager> walletList) {
        this.mContext = context;
        this.mWalletList = walletList;
    }

    @Override
    public WalletItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
        View convertView = inflater.inflate(R.layout.item_wallet, parent, false);

        return new WalletItemViewHolder(convertView);
    }

    public BaseWalletManager getItemAt(int pos) {
        return mWalletList.get(pos);
    }

    @Override
    public void onBindViewHolder(final WalletItemViewHolder holder, int position) {

        //syncList.put(holder, false);

        mSyncList.add(new Pair(holder, false));

        final BaseWalletManager wallet = mWalletList.get(position);
        String name = wallet.getName(mContext);
        String exchangeRate = CurrencyUtils.getFormattedCurrencyString(mContext, BRSharedPrefs.getPreferredFiatIso(mContext), new BigDecimal(wallet.getFiatExchangeRate(mContext)));
        String fiatBalance = CurrencyUtils.getFormattedCurrencyString(mContext, BRSharedPrefs.getPreferredFiatIso(mContext), new BigDecimal(wallet.getFiatBalance(mContext)));

        String cryptoBalance = CurrencyUtils.getFormattedCurrencyString(mContext, wallet.getIso(mContext), new BigDecimal(wallet.getCachedBalance(mContext)));
        final String iso = wallet.getIso(mContext);

        // Set wallet fields
        holder.mWalletName.setText(name);
        holder.mTradePrice.setText(exchangeRate);
        holder.mWalletBalanceUSD.setText(fiatBalance);
        holder.mWalletBalanceCurrency.setText(cryptoBalance);
        holder.mSyncingProgressBar.setVisibility(View.INVISIBLE);
        holder.mSyncing.setText("Waiting to Sync");
        holder.mWalletBalanceCurrency.setVisibility(View.INVISIBLE);


        if (wallet.getIso(mContext).equalsIgnoreCase(WalletBitcoinManager.getInstance(mContext).getIso(mContext))) {
            holder.mParent.setBackground(mContext.getResources().getDrawable(R.drawable.btc_card_shape, null));
        } else {
            holder.mParent.setBackground(mContext.getResources().getDrawable(R.drawable.bch_card_shape, null));

        }

        String lastUsedWalletIso = BRSharedPrefs.getCurrentWalletIso(mContext);

        BRConnectivityStatus connectivityChecker = new BRConnectivityStatus(mContext);

        int connectionStatus = connectivityChecker.isMobileDataOrWifiConnected();

        Log.d(TAG, "Current wallet ISO -> " + iso + ", last used wallet ISO -> " + lastUsedWalletIso);
        Log.d(TAG, "Connection status -> " + connectionStatus);


        // Mobile or Wifi is ON, sync the last used wallet
        if (iso.equals(lastUsedWalletIso)) {
            Log.d(TAG, "Current wallet is last used, Connection Status -> " + connectionStatus);
            if (connectionStatus == BRConnectivityStatus.MOBILE_ON || connectionStatus == BRConnectivityStatus.WIFI_ON || connectionStatus == BRConnectivityStatus.MOBILE_WIFI_ON) {

                syncWallet(wallet, holder);

                //SyncManager syncManager = SyncManager.getInstance();
                //syncManager.setProgressBar(holder.mSyncingProgressBar);
                //syncManager.startSyncingProgressThread();

            }
        } else {

        }


    }

    private void syncWallet(final BaseWalletManager wallet, final WalletItemViewHolder holder) {

        BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                if (wallet.getPeerManager().getConnectStatus() == BRCorePeer.ConnectStatus.Disconnected) {
                    wallet.getPeerManager().connect();
                    Log.e(TAG, "run: core connecting");
                }
            }
        });

        final Handler progressHandler = new Handler();
        Runnable progressRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "progressRunnable running!");
                final double syncProgress = wallet.getPeerManager().getSyncProgress(BRSharedPrefs.getStartHeight(mContext, wallet.getIso(mContext)));


                progressHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        // SYNCING
                        if (syncProgress > 0.0 && syncProgress < 1.0) {
                            int progress = (int) (syncProgress * 100);
                            Log.d(TAG, "Sync progress -> " + syncProgress);
                            Log.d(TAG, "Wallet ISO  -> " + wallet.getIso(mContext));
                            Log.d(TAG, "Sync status SYNCING");
                            holder.mSyncingProgressBar.setVisibility(View.VISIBLE);
                            holder.mSyncing.setText("Syncing ");
                            holder.mSyncingProgressBar.setProgress(progress);
                        }


                        // HAS NOT STARTED SYNCING
                        else if (syncProgress == 0.0) {
                            Log.d(TAG, "Sync progress -> " + syncProgress);
                            holder.mSyncingProgressBar.setVisibility(View.INVISIBLE);
                            holder.mSyncing.setText("Waiting to Sync");
                            holder.mSyncingProgressBar.setVisibility(View.INVISIBLE);
                            holder.mSyncingProgressBar.setProgress(0);
                            Log.d(TAG, "Wallet ISO  -> " + wallet.getIso(mContext));
                            Log.d(TAG, "Sync status NOT SYNCING");

                            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            params.addRule(RelativeLayout.ALIGN_LEFT, holder.mWalletBalanceUSD.getId());
                            holder.mSyncing.setLayoutParams(params);


                        }

                        // FINISHED SYNCING
                        else if (syncProgress >= 1) {
                            Log.d(TAG, "Sync progress -> " + syncProgress);
                            holder.mSyncingProgressBar.setVisibility(View.INVISIBLE);
                            holder.mSyncing.setVisibility(View.INVISIBLE);
                            holder.mWalletBalanceCurrency.setVisibility(View.VISIBLE);
                            holder.mSyncingProgressBar.setProgress(100);
                            holder.mWalletBalanceCurrency.setVisibility(View.VISIBLE);

                            long balance = BRSharedPrefs.getCachedBalance(mContext, wallet.getIso(mContext));
                            holder.mWalletBalanceCurrency.setText(String.valueOf(balance));
                            Log.d(TAG, "Wallet ISO  -> " + wallet.getIso(mContext));
                            Log.d(TAG, "Sync status FINISHED");

                            // Update this cell as already being synced
                            mSyncList.add(new Pair(holder, true));

                            // Once the currently used wallet is finished syncing, start
                            // syncing the next one on the list
                            int currentPosition = mWalletList.indexOf(wallet);

                            WalletItemViewHolder nextWalletHolder = mSyncList.get(currentPosition + 1).first;

                            syncWallet(mWalletList.get(currentPosition + 1), nextWalletHolder);

                            progressHandler.removeCallbacks(this);



                        }
                    }
                });

                progressHandler.postDelayed(this, 500);

            }
        };

        progressHandler.postDelayed(progressRunnable, 500);

    }

    @Override
    public int getItemCount() {
        return mWalletList.size();
    }

    public class WalletItemViewHolder extends RecyclerView.ViewHolder {

        public BRText mWalletName;
        public BRText mTradePrice;
        public BRText mWalletBalanceUSD;
        public BRText mWalletBalanceCurrency;
        public RelativeLayout mParent;
        public BRText mSyncing;
        public ProgressBar mSyncingProgressBar;

        public WalletItemViewHolder(View view) {
            super(view);

            mWalletName = view.findViewById(R.id.wallet_name);
            mTradePrice = view.findViewById(R.id.wallet_trade_price);
            mWalletBalanceUSD = view.findViewById(R.id.wallet_balance_usd);
            mWalletBalanceCurrency = view.findViewById(R.id.wallet_balance_currency);
            mParent = view.findViewById(R.id.wallet_card);
            mSyncing = view.findViewById(R.id.syncing);
            mSyncingProgressBar = view.findViewById(R.id.sync_progress);
        }
    }
}
