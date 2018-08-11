package com.samourai.wallet.whirlpool.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.samourai.wallet.R;
import com.samourai.wallet.whirlpool.models.Coin;

import java.util.ArrayList;

public class CoinsAdapter extends RecyclerView.Adapter<CoinsAdapter.ViewHolder> {

    private Context mContext;
    private ArrayList<Coin> mCoins;


    public CoinsAdapter(Context context, ArrayList<Coin> coins) {
        mCoins = coins;
        mContext = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final Coin coin = mCoins.get(position);

        holder.addressTxView.setText(coin.getAddress());
        holder.btcTxView.setText(String.valueOf(coin.getValue()).concat(" BTC"));
        holder.checkBox.setChecked(coin.getSelected());
        holder.checkBox.setTag(mCoins.get(position));

        holder.checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CompoundButton compoundButton = (CompoundButton) view;
                Coin mCoin = (Coin) compoundButton.getTag();
                mCoin.setSelected(compoundButton.isChecked());
                mCoins.get(position).setSelected(compoundButton.isChecked());
            }
        });

    }

    public ArrayList<Coin> getCoins() {
        return mCoins;
    }

    @Override
    public int getItemCount() {
        if (mCoins.isEmpty()) {
            return 0;
        }
        return mCoins.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private TextView btcTxView, addressTxView;
        private CheckBox checkBox;

        ViewHolder(View itemView) {
            super(itemView);
            btcTxView = itemView.findViewById(R.id.coin_item_btc_value);
            addressTxView = itemView.findViewById(R.id.coin_item_address);
            checkBox = itemView.findViewById(R.id.coin_item_checkbox);
        }
    }

}
