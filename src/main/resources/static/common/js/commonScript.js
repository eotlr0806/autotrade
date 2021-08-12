
/** 공통 선언 **/
const EXCHANGE_LIST      = {
    COINONE  : "COINONE",
    FOBLGATE : "FOBLGATE",
    DCOIN    : "DCOIN", 
    FLATA    : "FLATA",
    BITHUMBGLOBAL: "BITHUMBGLOBAL",
}

const AUTO_TRADE         = "/v1/trade/auto";
const EXCHANGE           = "/v1/exchanges";
const COIN               = "/v1/exchanges/coin";
const LIQUIDITY          = "/v1/trade/liquidity";
const ORDER_BOOK         = "/v1/orderbook";
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
        let returnData;

        await axios.get(url).
        then(response => {
            returnData = response;
        }).
        catch(error => {
            alert('error');
        })
        return returnData;
    },

    post : async (url, body) => {
        common.blockPage("요청중입니다.");
        let returnData;
        await axios.post(url, body).
        then(response => {
            common.unblockPage();
            returnData = response;
        }).
        catch(error => {
            common.noti("자전거래","서버 오류입니다. 관리자에게 문의하세요.","error");
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
            returnData = response;
        }).
        catch(error => {
            common.noti("자전거래","서버 오류입니다. 관리자에게 문의하세요.","error");
        });
        return returnData;
    },
}