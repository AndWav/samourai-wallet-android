package com.samourai.wallet.util;

import com.samourai.wallet.SamouraiWallet;

import org.bitcoinj.core.Address;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;


public class UTXOUtil {

    public enum AddressTypes {
        LEGACY,
        SEGWIT_COMPAT,
        SEGWIT_NATIVE;
    }

    private static UTXOUtil instance = null;

    private static HashMap<String,String> utxoTags = null;
    private static HashMap<String,String> utxoNotes = null;
    private static HashMap<String,Integer> utxoScores = null;

    private UTXOUtil() {
        ;
    }

    public static UTXOUtil getInstance() {

        if(instance == null) {
            utxoTags = new HashMap<String,String>();
            utxoNotes = new HashMap<String,String>();
            utxoScores = new HashMap<String,Integer>();
            instance = new UTXOUtil();
        }

        return instance;
    }

    public void reset() {
        utxoTags.clear();
        utxoNotes.clear();
        utxoScores.clear();
    }

    public void add(String utxo, String tag) {
        utxoTags.put(utxo, tag);
    }

    public String get(String utxo) {
        if (utxoTags.containsKey(utxo)) {
            return utxoTags.get(utxo);
        } else {
            return null;
        }

    }

    public HashMap<String, String> getTags() {
        return utxoTags;
    }

    public void remove(String utxo) {
        utxoTags.remove(utxo);
    }

    public void addNote(String utxo, String note) {
        utxoNotes.put(utxo, note);
    }

    public String getNote(String utxo) {
        if(utxoNotes.containsKey(utxo))  {
            return utxoNotes.get(utxo);
        }
        else    {
            return null;
        }

    }

    public HashMap<String,String> getNotes() {
        return utxoNotes;
    }

    public void removeNote(String utxo) {
        utxoNotes.remove(utxo);
    }

    public void addScore(String utxo, int score) {
        utxoScores.put(utxo, score);
    }

    public int getScore(String utxo) {
        if(utxoScores.containsKey(utxo))  {
            return utxoScores.get(utxo);
        }
        else    {
            return 0;
        }

    }

    public void incScore(String utxo, int score) {
        if(utxoScores.containsKey(utxo))  {
            utxoScores.put(utxo, utxoScores.get(utxo) + score);
        }
        else    {
            utxoScores.put(utxo, score);
        }

    }

    public HashMap<String,Integer> getScores() {
        return utxoScores;
    }

    public void removeScore(String utxo) {
        utxoScores.remove(utxo);
    }

    public JSONArray toJSON() {

        JSONArray utxos = new JSONArray();
        for (String key : utxoTags.keySet()) {
            JSONArray tag = new JSONArray();
            tag.put(key);
            tag.put(utxoTags.get(key));
            utxos.put(tag);
        }

        return utxos;
    }

    public void fromJSON(JSONArray utxos) {
        try {
            for (int i = 0; i < utxos.length(); i++) {
                JSONArray tag = (JSONArray) utxos.get(i);
                utxoTags.put((String) tag.get(0), (String) tag.get(1));
            }
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    public JSONArray toJSON_notes() {

        JSONArray utxos = new JSONArray();
        for(String key : utxoNotes.keySet()) {
            JSONArray note = new JSONArray();
            note.put(key);
            note.put(utxoNotes.get(key));
            utxos.put(note);
        }

        return utxos;
    }

    public void fromJSON_notes(JSONArray utxos) {
        try {
            for(int i = 0; i < utxos.length(); i++) {
                JSONArray note = (JSONArray)utxos.get(i);
                utxoNotes.put((String)note.get(0), (String)note.get(1));
            }
        }
        catch(JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    public JSONArray toJSON_scores() {

        JSONArray utxos = new JSONArray();
        for(String key : utxoScores.keySet()) {
            JSONArray score = new JSONArray();
            score.put(key);
            score.put(utxoScores.get(key));
            utxos.put(score);
        }

        return utxos;
    }

    public void fromJSON_scores(JSONArray utxos) {
        try {
            for(int i = 0; i < utxos.length(); i++) {
                JSONArray score = (JSONArray)utxos.get(i);
                utxoScores.put((String)score.get(0), (int)score.get(1));
            }
        }
        catch(JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static AddressTypes getAddressType(String address) {


        if (FormatsUtil.getInstance().isValidBech32(address)) {
            // is bech32: p2wpkh BIP84
            return AddressTypes.SEGWIT_NATIVE;
        } else if (Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
            // is P2SH wrapped segwit BIP49
            return AddressTypes.SEGWIT_COMPAT;
        } else {
            return AddressTypes.LEGACY;
        }
    }

}
