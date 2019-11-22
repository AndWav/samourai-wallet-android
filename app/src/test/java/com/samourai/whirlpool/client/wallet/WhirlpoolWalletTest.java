package com.samourai.whirlpool.client.wallet;

import com.samourai.http.client.AndroidHttpClient;
import com.samourai.http.client.IHttpClient;
import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.util.WebUtil;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.UnspentOutputWithKey;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.MixOrchestratorState;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.persist.FileWhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;

import junit.framework.Assert;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.TestNet3Params;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;

@Ignore
public class WhirlpoolWalletTest extends AbstractWhirlpoolTest {
    private Logger log = LoggerFactory.getLogger(WhirlpoolWalletTest.class.getSimpleName());
    private WhirlpoolWallet whirlpoolWallet;
    private HD_Wallet bip84w;
    private WhirlpoolWalletConfig config;

    private static final String SEED_WORDS = "all all all all all all all all all all all all";
    private static final String SEED_PASSPHRASE = "test";

    @Before
    public void setUp() throws Exception {
        super.setUp(TestNet3Params.get());

        // configure wallet
        boolean testnet = true;
        boolean onion = false;
        int mixsTarget = 5;
        String scode = null;

        // backendApi with mocked pushTx
        IHttpClient httpClient = new AndroidHttpClient(WebUtil.getInstance(getContext()));
        BackendApi backendApi = new BackendApi(httpClient, BackendServer.TESTNET.getBackendUrl(onion), null) {
            @Override
            public void pushTx(String txHex) throws Exception {
                log.info("pushTX ignored for test: "+txHex);
            }
        };

        File fileIndex = File.createTempFile("test-state", "test");
        File fileUtxo = File.createTempFile("test-utxos", "test");
        WhirlpoolWalletPersistHandler persistHandler =
                new FileWhirlpoolWalletPersistHandler(fileIndex, fileUtxo);

        // instanciate WhirlpoolWallet
        bip84w = computeBip84w(SEED_WORDS, SEED_PASSPHRASE);
        config = whirlpoolWalletService.computeWhirlpoolWalletConfig(getContext(), persistHandler, testnet, onion, mixsTarget, scode, httpClient, backendApi);
        whirlpoolWallet = whirlpoolWalletService.openWallet(config, bip84w);
    }

    @Test
    public void testStart() throws Exception {
        // start whirlpool wallet
        whirlpoolWallet.start();

        // list pools
        Collection<Pool> pools = whirlpoolWallet.getPools();
        Assert.assertTrue(!pools.isEmpty());

        // find pool by poolId
        Pool pool = whirlpoolWallet.findPoolById("0.01btc");
        Assert.assertNotNull(pool);

        // list premix utxos
        Collection<WhirlpoolUtxo> utxosPremix = whirlpoolWallet.getUtxosPremix();
        log.info(utxosPremix.size()+" PREMIX utxos:");
        ClientUtils.logWhirlpoolUtxos(utxosPremix);

        // list postmix utxos
        Collection<WhirlpoolUtxo> utxosPostmix = whirlpoolWallet.getUtxosPremix();
        log.info(utxosPostmix.size()+" POSTMIX utxos:");
        ClientUtils.logWhirlpoolUtxos(utxosPostmix);

        // keep running
        for(int i=0; i<2; i++) {
            MixOrchestratorState mixState = whirlpoolWallet.getState().getMixState();
            log.debug("WHIRLPOOL: "+mixState.getNbQueued()+" queued, "+mixState.getNbMixing()+" mixing: "+mixState.getUtxosMixing());

            synchronized (this) {
                wait(10000);
            }
        }
    }

    @Test
    public void testTx0() throws Exception {
        Collection<UnspentOutputWithKey> spendFroms = new LinkedList<>();

        ECKey ecKey = bip84w.getAccountAt(0).getChain(0).getAddressAt(61).getECKey();
        UnspentResponse.UnspentOutput unspentOutput = newUnspentOutput(
                "cc588cdcb368f894a41c372d1f905770b61ecb3fb8e5e01a97e7cedbf5e324ae", 1, 500000000);
        spendFroms.add(new UnspentOutputWithKey(unspentOutput, ecKey.getPrivKeyBytes()));

        Pool pool = whirlpoolWallet.findPoolById("0.01btc");
        Tx0 tx0 = whirlpoolWallet.tx0(spendFroms, pool, 1, 1, 1);

        Assert.assertEquals("b2160e06f8b48c9e4d1c66777b392cab6478960b49d48d9a580f80867bc64060", tx0.getTx().getHashAsString());
        Assert.assertEquals("01000000000101ae24e3f5dbcee7971ae0e5b83fcb1eb67057901f2d371ca494f868b3dc8c58cc0100000000ffffffff040000000000000000426a408a9eb379a45df4d4579118c64b64bbd327cd95ba826ac68f334155fd9ca4e3acd64acdfd75dd7c3cc5bc34d31af6c6e68b4db37eac62b574890f6cfc7b904d9950c30000000000001600143620976799aa57207001fcc99cf5c1aea99c176609430f00000000001600147e4a4628dd8fbd638681a728e39f7d92ada04070945dbd1d00000000160014df3a4bc83635917ad18621f3ba78cef6469c5f5902473044022008de95121879d0a887c24661b5a2609ae34deff9bcfb4b18652cdfb6f2528aa502203db055ed05ffb4f292f1ffcdbe82fbc4e35a961c0e522ce4409c79e821df8afc0121032e46baef8bcde0c3a19cadb378197fa31d69adb21535de3f84de699a1cf88b4500000000", new String(Hex.encode(tx0.getTx().bitcoinSerialize())));
    }
}