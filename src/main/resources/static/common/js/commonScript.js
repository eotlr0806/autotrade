
/** 공통 선언 **/
const CODE = {
    SUCCESS : 2000,
    FAIL    : 4000,
}

const EXCHANGE_LIST = {
    COINONE  : "COINONE",
    FOBLGATE : "FOBLGATE",
    DCOIN    : "DCOIN",
    FLATA    : "FLATA",
    BITHUMBGLOBAL: "BITHUMBGLOBAL",
    KUCOIN: "KUCOIN",
    OKEX: "OKEX"
}

const TRADE_LIST = {
    AUTOTRADE : "AUTOTRADE",
    LIQUIDITY : "LIQUIDITY",
    FISHING   : "FISHING"
}

const TRADE_STATUS = {
    RUN    : "RUN",
    STOP   : "STOP",
    STATUS : "STATUS",
    DELETE : "DELETE"
}

const API = {
    AUTO_TRADE  :"/v1/trade/auto",
    LIQUIDITY   :"/v1/trade/liquidity",
    FISHING     :"/v1/trade/fishing",
    SAVE_TRADE  :"/v1/trade/save",
    EXCHANGE    :"/v1/exchanges",
    COIN        :"/v1/exchanges/coin",
    ORDER_BOOK  :"/v1/orderbook",
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
            common.noti("ERROR","서버 오류입니다. 관리자에게 문의하세요.","error");
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

let trade = {
    postTrade : async (mode, action, itemId) => {
        // check mode
        let api;
        let title;
        let params;
        if (mode === TRADE_LIST.AUTOTRADE){
            api    = API.AUTO_TRADE;
            title  = "자전거래";
        }else if (mode === TRADE_LIST.LIQUIDITY){
            api    = API.LIQUIDITY;
            title  = "호가 유동성";
        }else if (mode === TRADE_LIST.FISHING){
            api    = API.FISHING;
            title  = "매매 긁기";
        }

        if(action == TRADE_STATUS.RUN){
            if (mode === TRADE_LIST.AUTOTRADE){
                params = trade.setDataBeforeAutoTradeRun();
            }else if (mode === TRADE_LIST.LIQUIDITY){
                params = trade.setDataBeforeLiquidityTrade();
            }else if (mode === TRADE_LIST.FISHING){
                params = trade.setDataBeforeFishingTrade();
            }

            if(params != CODE_ERROR){
                let returnVal = await common.post(api, params);
                if(returnVal.data != null){
                    if(returnVal.data.code == SUCCESS){
                        common.noti(title,"해당 내역으로 거래가 시작됩니다.","success");
                    }else{
                        common.noti(title,"해당 거래가 정상적으로 실행되지 않았습니다.\n로그아웃 후 다시 시도해주세요.","error");
                    }
                }else{
                    common.noti("자전거래","해당 자전거래가 정상적으로 실행되지 않았습니다.\n로그아웃 후 다시 시도해주세요.","error");
                }
            }
        }
        // 삭제
        else if(action == TRADE_STATUS.DELETE){
            params = {
                id: itemId,
                status: TRADE_STATUS.DELETE
            }

            let returnVal = await common.post(api, params);
            if(returnVal.data != null){
                if(returnVal.data.code == SUCCESS){
                    common.noti(title,"해당 거래가 삭제 됩니다.됩니다.","success");
                }else{
                    common.noti(title,"해당 거래가 정상적으로 삭제되지 않았습니다.\n로그아웃 후 다시 시도해주세요.","error");
                }
            }else{
                common.noti(title,"해당 거래가 정상적으로 삭제되지 않았습니다.\n로그아웃 후 다시 시도해주세요.","error");
            }
        }
    },


    // Autotrade start
    setDataBeforeAutoTradeRun : () => {

        if(!trade.validateCommon() || !trade.validateAutoTrade()){
            return CODE_ERROR;
        }
        let body = {
            minCnt      : $('#min_cnt').val(),
            maxCnt      : $('#max_cnt').val(),
            minSeconds  : $('#min_seconds').val(),
            maxSeconds  : $('#max_seconds').val(),
            exchange    : $('#filter_exchange').val(),
            coin        : $('#filter_coin').val(),
            mode        : clickedAutoMode,
            status      : RUN,
            userId      : loginUserId
        };
        return body;
    },

    // Liquidity Start
    setDataBeforeLiquidityTrade : () => {
        if(!trade.validateCommon() || !trade.validateLiquidityTrade()){
            return CODE_ERROR
        }

        let body = {
            minCnt      : $('#min_cnt_liquidity').val(),
            maxCnt      : $('#max_cnt_liquidity').val(),
            minSeconds  : $('#min_seconds_liquidity').val(),
            maxSeconds  : $('#max_seconds_liquidity').val(),
            randomTick  : $('#random_tick_liquidity').val(),
            rangeTick   : $('#range_tick_liquidity').val(),
            selfTick    : liquiditySelfTick.toString(),
            status      : RUN,
            mode        : clickedLiquidityMode,
            exchange    : $('#filter_exchange').val(),
            coin        : $('#filter_coin').val(),
            userId      : loginUserId
        }
        return body;
    },

    // Fishing Start
    setDataBeforeFishingTrade : () => {
        if(!fncObj.validateCommon() || !fncObj.validateFishingTrade()){
            return CODE_ERROR
        }

        let body = {
            minContractCnt     : $('#min_contract_fishing').val(),
            maxContractCnt     : $('#max_contract_fishing').val(),
            minExecuteCnt      : $('#min_execute_fishing').val(),
            maxExecuteCnt      : $('#max_execute_fishing').val(),
            minSeconds         : $('#min_seconds_fishing').val(),
            maxSeconds         : $('#max_seconds_fishing').val(),
            rangeTick          : $('#range_tick_fishing').val(),
            tickCnt            : $('#tick_cnt_fishing').val(),
            status             : RUN,
            mode               : (clickedFishingMode === 'RANDOM_FISHING_L') ? 'RANDOM' : ((clickedFishingMode === 'SELL_FISHING_L') ? 'SELL' : 'BUY'),
            exchange           : $('#filter_exchange').val(),
            coin               : $('#filter_coin').val(),
            userId             : loginUserId
        }
        return body;
    },
}