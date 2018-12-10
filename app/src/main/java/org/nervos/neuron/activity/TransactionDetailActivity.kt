package org.nervos.neuron.activity

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.Permission
import kotlinx.android.synthetic.main.activity_transaction_detail.*
import org.nervos.neuron.R
import org.nervos.neuron.activity.transactionlist.view.TransactionListActivity.TRANSACTION_STATUS
import org.nervos.neuron.activity.transactionlist.view.TransactionListActivity.TRANSACTION_TOKEN
import org.nervos.neuron.item.TokenItem
import org.nervos.neuron.item.WalletItem
import org.nervos.neuron.item.transaction.TransactionResponse
import org.nervos.neuron.service.http.AppChainRpcService
import org.nervos.neuron.service.http.NeuronSubscriber
import org.nervos.neuron.util.*
import org.nervos.neuron.util.db.DBWalletUtil
import org.nervos.neuron.util.db.SharePrefUtil
import org.nervos.neuron.util.ether.EtherUtil
import org.nervos.neuron.util.permission.PermissionUtil
import org.nervos.neuron.util.permission.RuntimeRationale
import org.nervos.neuron.util.url.HttpUrls
import org.nervos.neuron.view.TitleBar
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.io.IOException
import java.math.BigInteger

class TransactionDetailActivity : NBaseActivity(), View.OnClickListener {

    companion object {
        const val TRANSACTION_DETAIL = "TRANSACTION_DETAIL"
    }

    private var walletItem: WalletItem? = null
    private var transactionResponse: TransactionResponse? = null
    private var title: TitleBar? = null
    private var isEther = false

    override fun getContentLayout(): Int {
        return R.layout.activity_transaction_detail
    }

    override fun initView() {
        title = findViewById(R.id.title)
    }

    @SuppressLint("SetTextI18n")
    override fun initData() {
        walletItem = DBWalletUtil.getCurrentWallet(mActivity)
        transactionResponse = intent.getParcelableExtra(TRANSACTION_DETAIL)

        var tokenItem: TokenItem = intent.getParcelableExtra(TRANSACTION_TOKEN)
        if (EtherUtil.isEther(tokenItem)) {
            isEther = true
        }
        TokenLogoUtil.setLogo(tokenItem, mActivity, iv_token_icon)
        when (intent.getIntExtra(TRANSACTION_STATUS, TransactionResponse.PENDING)) {
            TransactionResponse.FAILED -> {
                Glide.with(this)
                        .load(R.drawable.bg_transaction_detail_failed)
                        .into(iv_status_bg)
                tv_status.text = resources.getString(R.string.transaction_detail_status_fail)
                tv_status.setTextColor(ContextCompat.getColor(this, R.color.transaction_detail_failed))
            }
            TransactionResponse.PENDING -> {
                Glide.with(this)
                        .load(R.drawable.bg_transaction_detail_pending)
                        .into(iv_status_bg)
                tv_status.text = resources.getString(R.string.transaction_detail_status_pending)
                tv_status.setTextColor(ContextCompat.getColor(this, R.color.transaction_detail_pending))
            }
            TransactionResponse.SUCCESS -> {
                Glide.with(this)
                        .load(R.drawable.bg_transaction_detail_success)
                        .into(iv_status_bg)
                tv_status.text = resources.getString(R.string.transaction_detail_status_success)
                tv_status.setTextColor(ContextCompat.getColor(this, R.color.transaction_detail_success))
            }
        }
        tv_token_unit.text = tokenItem.symbol
        tv_transaction_number.text = transactionResponse!!.hash
        tv_transaction_sender.text = transactionResponse!!.from
        tv_transaction_sender.setOnClickListener {
            copyText(transactionResponse!!.from)
        }
        iv_transaction_sender_copy.setOnClickListener {
            copyText(transactionResponse!!.from)
        }
        if (ConstantUtil.RPC_RESULT_ZERO == transactionResponse!!.to || transactionResponse!!.to.isEmpty()) {
            tv_transaction_receiver.text = resources.getString(R.string.contract_create)
            iv_transaction_receiver_copy.visibility = View.GONE
        } else {
            tv_transaction_receiver.text = transactionResponse!!.to
            tv_transaction_receiver.setOnClickListener {
                copyText(transactionResponse!!.to)
            }
            iv_transaction_receiver_copy.setOnClickListener {
                copyText(transactionResponse!!.to)
            }
        }
        val symbol = (if (transactionResponse!!.from.equals(walletItem!!.address, ignoreCase = true)) "-" else "+")
        tv_transaction_amount.text = symbol + NumberUtil.getDecimal8ENotation(transactionResponse!!.value)
        if (isEther) {
            tv_chain_name.text = SharePrefUtil.getString(ConstantUtil.ETH_NET, ConstantUtil.ETH_MAINNET).replace("_", " ")
            if (!TextUtils.isEmpty(transactionResponse!!.gasPrice)) {
                val gasPriceBig = BigInteger(transactionResponse!!.gasPrice)
                val gasUsedBig = BigInteger(transactionResponse!!.gasUsed)
                tv_transaction_gas.text = NumberUtil.getEthFromWeiForStringDecimal8(gasPriceBig.multiply(gasUsedBig)) + transactionResponse!!.nativeSymbol
                tv_transaction_gas_price.text = Convert.fromWei(gasPriceBig.toString(), Convert.Unit.GWEI).toString() + " " + ConstantUtil.GWEI
            }
            tv_transaction_blockchain_no!!.text = transactionResponse!!.blockNumber
            Glide.with(this)
                    .load(R.drawable.icon_eth_microscope)
                    .into(iv_microscope)
        } else {
            if (tokenItem.chainId != "1") {
                line3.visibility = View.GONE
                iv_microscope.visibility = View.GONE
                tv_microscope.visibility = View.GONE
                iv_microscope_arrow.visibility = View.GONE
            }

            Glide.with(this)
                    .load(R.drawable.icon_appchain_microscope)
                    .into(iv_microscope)

            tv_chain_name.text = transactionResponse!!.chainName
            tv_transaction_gas_price_title.text = resources.getString(R.string.transaction_quota_price)

            try {
                val blockNumber = Numeric.toBigInt(transactionResponse!!.blockNumber).toString(10)
                tv_transaction_blockchain_no!!.text = blockNumber.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            AppChainRpcService.getQuotaPrice(transactionResponse!!.from)
                    .subscribe(object : NeuronSubscriber<String>() {
                        override fun onNext(price: String) {
                            super.onNext(price)
                            var gasUsed = Numeric.toBigInt(transactionResponse!!.gasUsed)
                            var gasPrice = Numeric.toBigInt(HexUtils.IntToHex(price.toInt()))
                            var gas = gasUsed.multiply(gasPrice)
                            tv_transaction_gas.text =
                                    NumberUtil.getEthFromWeiForStringDecimal8(gas) + transactionResponse!!.nativeSymbol
                            tv_transaction_gas_price.text = Convert.fromWei(price, Convert.Unit.GWEI).toString() + " " + ConstantUtil.GWEI
                        }

                        override fun onError(e: Throwable?) {
                        }
                    })
        }

        tv_transaction_blockchain_time.text = transactionResponse!!.date

        tv_transaction_receiver!!.setOnClickListener {
            if (transactionResponse!!.to != ConstantUtil.RPC_RESULT_ZERO && !transactionResponse!!.to.isEmpty())
                copyText(transactionResponse!!.to)
        }
        tv_transaction_sender!!.setOnClickListener { copyText(transactionResponse!!.from) }
        tv_transaction_number!!.setOnClickListener { copyText(transactionResponse!!.hash) }
    }

    override fun initAction() {
        title!!.setOnRightClickListener {
            AndPermission.with(mActivity)
                    .runtime()
                    .permission(*Permission.Group.STORAGE)
                    .rationale(RuntimeRationale())
                    .onGranted {
                        try {
                            SharePicUtils.savePic(ConstantUtil.IMG_SAVE_PATH + transactionResponse!!.blockNumber +
                                    ".png", SharePicUtils.getCacheBitmapFromView(cl_share))
                            SharePicUtils.SharePic(this, ConstantUtil.IMG_SAVE_PATH + transactionResponse!!.blockNumber + ".png")
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    .onDenied { permissions ->
                        dismissProgressBar()
                        PermissionUtil.showSettingDialog(mActivity, permissions)
                    }
                    .start()
        }
        title!!.setOnLeftClickListener { finish() }
        iv_microscope.setOnClickListener(this)
        tv_microscope.setOnClickListener(this)
        iv_microscope_arrow.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.iv_microscope, R.id.tv_microscope, R.id.iv_microscope_arrow -> {
                if (isEther) {
                    SimpleWebActivity.gotoSimpleWeb(this, String.format(EtherUtil.getEtherTransactionDetailUrl(), transactionResponse!!.hash))
                } else {
                    SimpleWebActivity.gotoSimpleWeb(this, String.format(HttpUrls.APPCHAIN_TEST_TRANSACTION_DETAIL, transactionResponse!!.hash))
                }
            }
        }
    }

    private fun copyText(value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val mClipData = ClipData.newPlainText("value", value)
        cm.primaryClip = mClipData
        Toast.makeText(mActivity, R.string.copy_success, Toast.LENGTH_SHORT).show()
    }

}
