
/** 공통 선언 **/
const CODE = {
    SUCCESS : 2000,
    FAIL    : 4000,
    NO_DATA : 4001,
}

const EXCHANGE_LIST = {
    COINONE  : "COINONE",
    FOBLGATE : "FOBLGATE",
    DCOIN    : "DCOIN",
    FLATA    : "FLATA",
    BITHUMBGLOBAL: "BITHUMBGLOBAL",
    KUCOIN: "KUCOIN",
    OKEX: "OKEX",
    LBANK: "LBANK",
    DIGIFINEX: "DIGIFINEX",
    XTCOM: "XTCOM",
    COINSBIT: "COINSBIT"
}

const TRADE_LIST = {
    AUTOTRADE      : "AUTOTRADE",
    LIQUIDITY      : "LIQUIDITY",
    FISHING        : "FISHING",
    REALTIME_SYNC  : "REALTIME_SYNC"
}

const TRADE_STATUS = {
    RUN    : "RUN",
    STOP   : "STOP",
    STATUS : "STATUS",
    DELETE : "DELETE"
}

const API = {
    AUTO_TRADE    :"/v1/trade/auto",
    LIQUIDITY     :"/v1/trade/liquidity",
    FISHING       :"/v1/trade/fishing",
    REALTIME_SYNC :"/v1/trade/realtime_sync",
    SAVE_TRADE    :"/v1/trade/save",
    EXCHANGE      :"/v1/exchanges",
    COIN          :"/v1/exchanges/coin",
    ORDER_BOOK    :"/v1/orderbook",
}

const CODE_ERROR         = -1000;
const SUCCESS            = 200;



/** 공통으로 사용할수 있는 메서드 **/
let common = {

    // Notification
    noti : (title, text, type) => {
        let style;
        if(type === 'error') style = 'bg-danger';
        else if (type === 'success') style = 'bg-primary';

        new PNotify({
            title: title,
            text: text,
            addclass: style// 'bg-danger' , 'bg-primary' , 'bg-success', 'bg-warning', 'bg-info'
        });

        if(type === 'error') return false;
        else return true;
    },

    // Block UI
    blockPage : (msg) => {
        $.blockUI({
            message: `<i class="icon-spinner4 spinner">${msg}</i>`,
            overlayCSS: {
                backgroundColor: '#1b2024',
                opacity: 0.8,
                zIndex: 1200,
                cursor: 'wait'
            },
            css: {
                border: 0,
                color: '#fff',
                padding: 0,
                zIndex: 1201,
                backgroundColor: 'transparent'
            }
        });
    },
    // unblock UI
    unblockPage : () => {
        $.unblockUI();
    },

    onClickCheckClass: id => {
        let str = '#' + id;
        if ($(str).hasClass('checked')) {
            $(str).removeClass('checked');
        } else {
            $(str).addClass('checked');
        }
    },

    /** API List **/
    get : async (url) => {
        let returnData = null;

        await axios.get(url).
        then(response => {
            if (response.data.status === CODE.SUCCESS){
                returnData = response.data;
            }else{
                console.log(`[GET API ERROR] url: ${url}, code:${response.data.status}, msg: ${response.data.msg}`);
            }
        }).
        catch(error => {
            console.log(`[GET API ERROR] url: ${url}, error: ${error}`);
        })
        return returnData;
    },

    post : async (url, body) => {
        common.blockPage("요청중입니다.");
        let returnData = null;
        await axios.post(url, body).
        then(response => {
            common.unblockPage();
            if (response.data.status === CODE.SUCCESS){
                returnData = response.data;
            }else{
                console.log(`[POST API ERROR] url: ${url}, code:${response.data.status}, msg: ${response.data.msg}`);
            }
        }).
        catch(error => {
            console.log(`[POST API ERROR] url: ${url}, error: ${error}`);
        });
        return returnData;
    },

    delete : async (url, body) => {
        common.blockPage("요청중입니다.");
        let returnData;
        await axios.delete(url, {
            data : body
        }).
        then(response => {
            common.unblockPage();
            if (response.data.status === CODE.SUCCESS){
                returnData = response.data;
            }else{
                console.log(`[DELETE API ERROR] url: ${url}, code:${response.data.status}, msg: ${response.data.msg}`);
            }

        }).
        catch(error => {
            console.log(`[DELETE API ERROR] url: ${url}, error: ${error}`);
        });
        return returnData;
    },
}