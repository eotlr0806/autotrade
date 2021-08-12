




var data_cnt;
var queryURL ;
var paraMeters;

var _keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";


const commonAdmin = {
	// Notification
	noti : (title, text, type) => {
		let style;
		if(type === 'error') style = 'bg-danger';
		else if (type === 'success') style = 'bg-primary';

		new PNotify({
			title: title,
			text: text,
			addclass: style + ' stack-bottom-right'// 'bg-danger' , 'bg-primary' , 'bg-success', 'bg-warning', 'bg-info'
		});

		if(type === 'error') return false;
		else return true;
	},
	// grant checked property
	onClickRadioBox: id => {
		$('#' + id).prop('checked', 'checked');
	},

	/* set checked class */
	onClickCheckClass: id => {
		let str = '#' + id;
		if ($(str).hasClass('checked')) {
			$(str).removeClass('checked');
		} else {
			$(str).addClass('checked');
		}
	},

	numberToString : (num,commaCnt, commaType) => {
		let numStr = num.toString();
		let result = '';
		let cnt = 1;
		let comma    = commaCnt  || 3
		let commaStr = commaType || ','
		for(let i = numStr.length - 1; i >= 0; i--){
			if(cnt%comma == 0 && i != 0)  result = `${commaStr}${numStr.charAt(i)}` + result;
			else result = numStr.charAt(i) + result;

			cnt++;
		}
		return result;
	},

	addDate : (arr,type,addDay) => {
		let date = new Date(arr[0],(Number(arr[1])-1),arr[2],0,0,0);
		let addVal = Number(addDay) || 0;

		date.setDate(date.getDate() + addVal);

		let year  = date.getFullYear();
		let month = ((date.getMonth()+1).toString().length < 2) ? '0' + (date.getMonth()+1) : (date.getMonth()+1);
		let day   = (date.getDate().toString().length < 2) ? '0' + date.getDate() : date.getDate();

		let returnVal;
		if(type == 'YYYY-MM-DD'){
			returnVal = `${year}-${month}-${day}`;
		}else if(type == 'YYYYMMDD') {
			returnVal = `${year}${month}${day}`;
		}else if(type == undefined){
			returnVal = `${year}-${month}-${day}`;
		}

		return returnVal;
	},

	// 예약 조회 시 시간 확인
	checkVaildTimeModal : (date) => {
		let now = new Date();
		now.setHours(0,0,0,0);
		let selectedTime = new Date(date + 'T00:00:00');

		if((now - selectedTime) > 0) return -1;
		else if((now - selectedTime) == 0) return 0;
		else return 1;
	},


};


var Common = {

	deleteCookie : function(cookie_name) {
		document.cookie = cookie_name + '=';
	} ,

	setCookie : function (cookie_name , value) {
		var days = 7;
		var exdate = new Date();
		exdate.setDate(exdate.getDate() + days);
		// 설정 일수만큼 현재시간에 만료값으로 지정

		var cookie_value = escape(value) + ((days == null) ? '' : ';    expires=' + exdate.toUTCString());
		document.cookie = cookie_name + '=' + cookie_value;
	},

	getCookie : function(cookie_name) {
			var x, y;
			var val = document.cookie.split(';');

			for (var i = 0; i < val.length; i++) {
				x = val[i].substr(0, val[i].indexOf('='));
				y = val[i].substr(val[i].indexOf('=') + 1);
				x = x.replace(/^\s+|\s+$/g, ''); // 앞과 뒤의 공백 제거하기
				if (x == cookie_name) {
					return unescape(y); // unescape로 디코딩 후 값 리턴
				}
			}
	},


	getParamValue : function () {
		var params = {};
		window.location.search.replace(/[?&]+([^=&]+)=([^&]*)/gi, function(str, key, value) { params[key] = value; });
		return params;
	},


	removeComma : function (str) {
		n = parseInt(str.replace(/,/g,""));
		return n;
	},

	comma : function(str) {
		return str.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
	},

	getRemainDay : function (date) {
		var arr1 = date.split('T')[0];
		var arr2 = arr1.split('-');

		var today = new Date();
		var byDate = new Date(arr2[0], arr2[1], arr2[2]);

		console.log(arr2[0] + '-' +arr2[1] + '-' + arr2[2]);

		var tempDate = new Date(today.getFullYear() , today.getMonth()+1 , today.getDate());
		console.log(tempDate.getFullYear() + '-' + tempDate.getMonth() + '-' + tempDate.getDate());
		var diff = byDate - tempDate;
		var currDay = 24 * 60 * 60 * 1000;// 시 * 분 * 초 * 밀리세컨
		var currMonth = currDay * 30;// 월 만듬
		var currYear = currMonth * 12; // 년 만듬
		console.log('diff : ' + diff + ' , currDay : ' + currDay);
		return parseInt(diff/currDay);
	},



    /**
     * 페이지 이동
     */
    goPage : function(page) {
    	location.href = page;
    },
    
    /**
     * 페이지 reload
     */    
    reloadPage : function() {
        location.reload();
    },
        
    /**
     * ajax 호출 (response XML)
     * @param parameter for ajax
     * @param callback function
     */
    ajaxXML : function(_params, _func) {
        var self = this;
        
        // ajax 요청시 캐싱방지 파라미터 추가
        $.extend(_params.data, {
            "nocache" : (new Date).getTime()
        });
        
        $.ajax({
            type : _params.type || "GET",
            url : _params.url,
            async : (_params.async != undefined) ? _params.async : true,
            dataType : "xml",
            data : _params.data,
            success: function(xml) {
                xml = self.stringToXml(xml);
                var $xml = $(xml).find("xmlroot");
                if($xml.find("result > code").text().match("1")) {
                    /* 정상 */
                    _func($xml);
                } else {
                    /* 정상적인 XML이 아닌경우 */
                    alert("-.- xml error!");
                }
            },
            error: function (request, status, error) {
            	//alert("Error Common.ajaxXML = "+error);
            	alert("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
            }
        });
    },
    
    /**
     * ajax 호출 (response JSON)
     * @param parameter for ajax
     * @param callback function
     */
    ajaxJSON : function(_params, _func) {
        var self = this;
        var url = "";

        url = _params.url;

        //console.log("ajaxJSON url >>> " + url);
        
        // ajax 요청시 캐싱방지 파라미터 추가
        $.extend(_params.data, {
            "nocache" : (new Date).getTime()
        });
        
        $.ajax({
            type : _params.type || "GET",
            url : url,
            async : (_params.async != undefined) ? _params.async : true,
            dataType : "json",
            data : _params.data,
            success: function(data) {
            	if(data.responseCode && data.responseCode == 401) {
					document.location.href="/logout";
				}
            	_func(data);
            },
            error: function (request, status, error) {
            	console.log("Error Common.ajaxJSON = "+request+" : "+status+" : "+error);
            	if(_params.type == "POST"){
            		//alert("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
					_func('FAIL');
					console.log("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
            	}else{
            		console.log("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
            	}
            }
        });
    },

	ajaxJSONV2 : function(_params, _func) {
		var self = this;
		var url = "";

		url = _params.url;

		//console.log("ajaxJSON url >>> " + url);

		// ajax 요청시 캐싱방지 파라미터 추가
		$.extend(_params.data, {
			"nocache" : (new Date).getTime()
		});

		$.ajax({
			type : _params.type || "GET",
			url : url,
			async : (_params.async != undefined) ? _params.async : true,
			dataType : "json",
			contentType: 'application/json',
			data : _params.data,
			success: function(data) {
				if(data.responseCode && data.responseCode == 401) {
					document.location.href="/logout";
				}
				_func(data);
			},
			error: function (request, status, error) {
				console.log("Error Common.ajaxJSON = "+request+" : "+status+" : "+error);
				if(_params.type == "POST"){
					alert("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
				}else{
					console.log("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
				}
			}
		});
	},
    
    /**
     * ajax 호출 - LoadingBar(response JSON)
     * @param parameter for ajax
     * @param callback function
     */
    ajaxLoadingJSON : function(_params, _func) {
        var self = this;
        var loading = $("#"+_params.loading);       
        
        var url = "";
        
        url = _params.url;
        
        console.log("ajaxLoadingJSON url >>> " + url);
        
        // ajax 요청시 캐싱방지 파라미터 추가
        $.extend(_params.data, {
            "nocache" : (new Date).getTime()
        });
        
        $.ajax({
            type : _params.type || "GET",
            url : url,
            async : (_params.async != undefined) ? _params.async : true,
            dataType : "json",
            data : _params.data,
            beforeSend:function(){	
            	loading.css("position");
            	loading.addClass("position-relative");
            	loading.append('<div class="widget-box-overlay"><i class="'+ace.vars.icon+'loading-icon fa fa-spinner fa-spin fa-2x white"></i></div>');
            },
            success: function(data) {
            	_func(data);
            },
            error: function (request, status, error) {
            	console.log("Error Common.ajaxJSON = "+request+" : "+status+" : "+error);
            	if(_params.type == "POST"){
            		alert("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
            	}else{
            		console.log("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
            	}
            },
            complete: function(){
            	loading.find(".widget-box-overlay").remove();
            	loading.removeClass("position-relative");
            }
            
        });
    },
    
    /**
     * ajax 호출 - FileUpload(response JSON)
     * @param parameter for ajax
     * @param callback function
     */
    ajaxFileUploadJSON : function(_params, _func) {
        var self = this;
        var loading = $("#"+_params.loading);
        
        // ajax 요청시 캐싱방지 파라미터 추가
        $.extend(_params.data, {
            "nocache" : (new Date).getTime()
        });
        
        $.ajax({
            type : _params.type || "GET",
            url : _params.url,
            async : (_params.async != undefined) ? _params.async : true,
            dataType : "json",
            data : _params.data,
            processData: false, 
			contentType: false,
			beforeSend:function(){	
				loading.css("position");
                loading.addClass("position-relative");
            	loading.append('<div class="widget-box-overlay"><i class="'+ace.vars.icon+'loading-icon fa fa-spinner fa-spin fa-2x white"></i></div>');
			},
            success: function(data) {            	
            	_func(data);
            },
            error: function (request, status, error) {
            	console.log("Error Common.ajaxJSON = "+request+" : "+status+" : "+error);
            	if(_params.type == "POST"){
            		alert("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
            	}else{
            		console.log("CRUD 작업이 실패 하였습니다. 다시 시도하세요!");
            	}
            },
            complete: function(){
            	loading.find(".widget-box-overlay").remove();
            	loading.removeClass("position-relative");
            }
        });
    },
    
    /**
     * string을 xml로 변환 (IE에서 xml을 text로 인식하는경우 처리)
     * @param str
     */
    stringToXml : function(str) {
        if (typeof str == typeof "string" 
                && typeof ActiveXObject != typeof undefined) {
            var xml = new ActiveXObject("Microsoft.XMLDOM");
            xml.loadXML(str);
            str = xml;
        }
        return str;
    }
};


// loading image 영역을 만든다.
function makeLoadingDiv(){
	 if (document.getElementById("divLoading") == null) 
	 {
		  var str = '';
		  str += "<div id='divLoading' style='display：none;z-index:1002;position：absolute;'>";
		  //str += " <img src='./images/loading.jpg' alt='로딩중..' />";
		  str += "</div>";
		  
		  $("body").append(str);
	 }
}

// url : 요청 주소, param : server 전송값, callback : 요청후 함수(전달 data), error: error 호출 함수,indicator : 로딩중 이미지 노출 유무
function ajaxCall(url,param,callback,error,indicator){
	 if(indicator)   
	 {
		  makeLoadingDiv();

		  $("#divLoading").ajaxStart(function(){
			$(this).show();
			//console.log("div show");
		  }); 
	 }		 

	 $.ajax({
		 type : "POST",
		 url : url,
		 data : param,
		 datatype : "json",
		 beforeSend : function(xhr) {
		 },
		 success : callback,
		 error : error,
		 complete : function(xhr, textStatus) {
		 },
		 timeout : 10000
	 });
	 
	 if(indicator)
	 {
		  $("#divLoading").ajaxStop(function(){
		   $(this).hide();
		   //console.log("div hide");
		  });
	 }
} 

// ajax error callback function
/*function errorFunc(xhr, textStatus, errorThrown){
	if ( xhr.readyState == 4 ) {
		if (xhr.status == 400) {
			var data = jQuery.parseJSON(xhr.responseText);
			var invalidMessage = '';
			$.each(data.fielderrors,function(n,value){
				invalidMessage += value+'\n';		
			});
			alert(invalidMessage);
		} else if (xhr.status == 406) {
			document.location.href="/cls-admin-web/ajaxjsp/goLogin.jsp";
			
		} else if(xhr.status == 500) {
			var data = jQuery.parseJSON(xhr.responseText);
			var invalidMessage = data.exception;
			alert(invalidMessage);
		} else {
			alert(xhr.responseText);
		}		    
	} else {
		alert("Failed to load resource");
	}	
}*/

//ajax error callback function
function errorFunc(xhr, textStatus, errorThrown){
	try {
		if ( xhr.readyState == 4 ) {
			if (xhr.status == 400) {
				var data = jQuery.parseJSON(xhr.responseText);
				var invalidMessage = '';
				$.each(data.fielderrors,function(n,value){
					invalidMessage += value+'\n';
				});
				alert(invalidMessage);
			} else if (xhr.status == 406) {
				//document.location.href="/cls-admin-web/ajaxjsp/goLogin.jsp";
				alert("errorFunc = 406");
				
			} else if(xhr.status == 500) {
				var data = jQuery.parseJSON(xhr.responseText);
				var invalidMessage = data.exception;
				alert(invalidMessage);
			} else {
				alert(xhr.responseText);
			}
		} else {
			alert("Failed to load resource");
		}
	} catch(e) {
		alert(e);
	}
}


// page list display function
function pagingList(obj, currentPage){
	//console.log("currentPage="+ currentPage);
	
	if(currentPage == undefined) { 
        selectedPage = 1;
	} else {
	    selectedPage = parseInt(currentPage / ITEMS_PER_PAGE) + 1;	
	    //console.log("selectedPage="+selectedPage);
	}
	
	if(total_page && total_page > 0){
		obj.empty();
		obj.zPaging(total_page, pageParm, {
			startPageIdx: 0,
			endPageIdx: 5,
			totalIndx: 1
		}, {
			align: "center",
			key: "zPage",
			movement: 10,
			perPage: 10,
			selectedPage: selectedPage,
			linkWidth: 25,
			linkTitle: "#NO#",
			viewInfo: true,
			infoTxt: "Page <strong>#now#</strong> of #total#",
			goFirstTxt: "FIRST",
			goPrevTxt: "PREV",
			goNowTxt: "NOW",
			goNextTxt: "NEXT",
			goLastTxt: "LAST",
			callBack: pageCall
		}, {
			nowPage: "nowPage",
			normalPage: "normalPage",
			infoPage: "infoPage",
			infoPageFirst: "infoPageFirst",
			infoPagePrev: "infoPagePrev",
			infoPageNow: "infoPageNow",
			infoPageNext: "infoPageNext",
			infoPageLast: "infoPageLast"
		});
		obj.css("position","relative");
		var page_width = obj.children('div').first().width();
		obj.css("width",page_width + 20);	
		//console.log("page_qwidth==="+page_width);
		
		var page_id = obj.children('div').first().attr('id');
		$('#'+page_id+'_prev').css("width",10);
		$('#'+page_id+'_first').css("width",10);
		//console.log("page_qid==="+page_id);		
	} else {
		obj.empty();
	}

}

// 달력 노출
function datepick(obj,flag){
	obj.datepicker({ dateFormat: 'yy.mm.dd', showOn: 'button', buttonImage: '../../images/common/ico_calendar.gif' });
	
	if(flag == 'Y'){
		var now = new Date();	
		var year = now.getFullYear();
		var month = now.getMonth() + 1;
		var day = now.getDate();
		if(month < 10) month = "0" + month;
		if(day < 10) day = "0" + day;
		
		obj.val(year+'.'+month+'.'+day);	
	}
}

// div mask
function divMask(obj, mask){
	
	obj.text(
			$.mask.string( obj.text(), mask )
	);	
	return;
}

//input mask
function inputMask(obj, mask){	
	obj.setMask(mask);	
	return;
}

//오늘 날짜를 mask형태로 만든다.
//mask(-) : 2014-02-20
//mask(.) : 2014.02.20
function getCurrentDate(mask){
	var now = new Date();	
	var year = now.getFullYear();
	var month = now.getMonth() + 1;
	var day = now.getDate();
	if(month < 10) month = "0" + month;
	if(day < 10) day = "0" + day;
	
	if(mask == "") mask = "-";
	
	return (year+mask+month+mask+day);
}

// number format
/**
 * Formats the number according to the 'format' string; adherses to the american number standard where a comma is inserted after every 3 digits.
 *  note: there should be only 1 contiguous number in the format, where a number consists of digits, period, and commas
 *        any other characters can be wrapped around this number, including '$', '%', or text
 *        examples (123456.789):
 *          '0' - (123456) show only digits, no precision
 *          '0.00' - (123456.78) show only digits, 2 precision
 *          '0.0000' - (123456.7890) show only digits, 4 precision
 *          '0,000' - (123,456) show comma and digits, no precision
 *          '0,000.00' - (123,456.78) show comma and digits, 2 precision
 *          '0,0.00' - (123,456.78) shortcut method, show comma and digits, 2 precision
 *
 * @method format
 * @param format {string} the way you would like to format this text
 * @return {string} the formatted number
 * @public
 */
Number.prototype.format = function(format) {
	if (typeof format != 'string') {return '';} // sanity check

	var hasComma = -1 < format.indexOf(','),
		psplit = format.stripNonNumeric().split('.'),
		that = this;

	// compute precision
	if (1 < psplit.length) {
		// fix number precision
		that = that.toFixed(psplit[1].length);
	}
	// error: too many periods
	else if (2 < psplit.length) {
		throw('NumberFormatException: invalid format, formats should have no more than 1 period: ' + format);
	}
	// remove precision
	else {
		that = that.toFixed(0);
	}

	// get the string now that precision is correct
	var fnum = that.toString();

	// format has comma, then compute commas
	if (hasComma) {
		// remove precision for computation
		psplit = fnum.split('.');
		
		var cnum = psplit[0],
			parr = [],
			j = cnum.length,
			m = Math.floor(j / 3),
			n = cnum.length % 3 || 3; // n cannot be ZERO or causes infinite loop

		// break the number into chunks of 3 digits; first chunk may be less than 3
		for (var i = 0; i < j; i += n) {
			if (i != 0) {n = 3;}
			parr[parr.length] = cnum.substr(i, n);
			m -= 1;
		}

		// put chunks back together, separated by comma
		fnum = parr.join(',');

		// add the precision back in
		if (psplit[1]) {fnum += '.' + psplit[1];}
	}

	//console.log("------------------");
	// replace the number portion of the format with fnum
	return format.replace(/[\d,?\.?]+/, fnum);
};

// Number.prototype.format에서 호출하는 함수
String.prototype.stripNonNumeric = function(){
	var str = this;
	str += '';
	var rgx = /^\d|\.|-$/;
	var out = '';
	for( var i = 0; i < str.length; i++ )
	{
		if( rgx.test( str.charAt(i) ) ){
			if( !( ( str.charAt(i) == '.' && out.indexOf( '.' ) != -1 ) ||
				( str.charAt(i) == '-' && out.length != 0 ) ) ){
				out += str.charAt(i);
			}
		}
	}
	return out;
};

//숫자만 체크 확인
//사용법 <input type="text" id="n" maxlength="4" class="nb" value="" onkeyUp="this.value.only_number();" />
String.prototype.only_number = function(){
	var rtn = this.replace(/[^0-9]/gi, '');
	if(this.length != rtn.length){
		alert("숫자만 입력하세요!");
		return false;
	}
		
	return true;
};

// 숫자만 가능
function chkOnlyNumbers(obj){
	
	obj.bind('keydown',function(event){
	    var keyCode = event.which;
	    
	    // 48-57 Standard Keyboard Numbers
	    var isStandard = (keyCode > 47 && keyCode < 58);

	    // 96-105 Extended Keyboard Numbers (aka Keypad)
	    var isExtended = (keyCode > 95 && keyCode < 106);

	    var validKeyCodes = ',8,9,37,38,39,40,46,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    if ( isStandard || isExtended || isOther ){
	        return true;
	    } else {
	        return false;
	    }

	}).bind('blur',function(){

	    var pattern = new RegExp('[^0-9]+', 'g');

	    var $input = $(this);
	    var value = $input.val();

	    value = value.replace(pattern, '');
	    $input.val( value );
	});	
	
}

//숫자만 가능 input 속성에 onkeyup="checkNum(this);" 적용
function checkNum(obj){
	var word = obj.value;
	var str = "-1234567890";
	
	for(i=0; i<word.length; i++){
		if(str.indexOf(word.charAt(i)) < 0){
			alert("숫자만 입력하세요!");
			obj.value = "";
			obj.focus = "";
			return false;
		}
	}
}

// 숫자와 특수문자 가능
function chkOnlyCriteria(obj){

	obj.bind('keypress',function(event){
	    var keyCode = event.which;
	    //console.log(keyCode);
	    // 48-57 Standard Keyboard Numbers 
	    var isStandard = (keyCode > 47 && keyCode < 58);

	    var validKeyCodes = ',8,9,32,37,39,40,46,60,61,62,88,65,78,68,97,100,110,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    if ( isStandard || isOther ){
	        return true;
	    } else {
	        return false;
	    }
	});		
}

// 영문 대문자만 가능
function chkOnlyCapital(obj){
	
	obj.bind('keypress',function(event){
	    var keyCode = event.which;

	    // 48-57 Standard Keyboard Numbers
	    var isStandard = (keyCode > 64 && keyCode < 91);

	    // 8 Backspace, 46 Forward Delete
	    // 37 Left Arrow, 38 Up Arrow, 39 Right Arrow, 40 Down Arrow
	    var validKeyCodes = ',8,9,37,38,39,40,46,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    if ( isStandard || isOther ){
	        return true;
	    } else {
	        return false;
	    }

	});	
}

//영문 소문자만 가능
function chkOnlyMinuscule(obj){
	
	obj.bind('keypress',function(event){
	    var keyCode = event.which;
	    
	    //console.log("keyCode="+keyCode);
	    //if(keyCode === null) console.log("aaa");

	    // 48-57 Standard Keyboard Numbers
	    var isStandard = (keyCode >= 97 && keyCode <= 122);

	    // 8 Backspace, 46 Forward Delete
	    // 37 Left Arrow, 38 Up Arrow, 39 Right Arrow, 40 Down Arrow
	    var validKeyCodes = ',8,9,37,38,39,40,46,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    if ( isStandard || isOther ){
	        return true;
	    } else {
	        return false;
	    }

	});	
}


//한글만 가능
function chkOnlyHangle(obj){
	
	obj.bind('keypress',function(event){
	    var keyCode = event.which;

	    //console.log("keyCode="+keyCode);
	    
	    // 48-57 Standard Keyboard Numbers
	    var isStandard = (keyCode < 32 && keyCode > 126);

	    // 8 Backspace, 46 Forward Delete
	    // 37 Left Arrow, 38 Up Arrow, 39 Right Arrow, 40 Down Arrow
	    var validKeyCodes = ',8,9,37,38,39,40,46,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    if ( isStandard || isOther ){
	        return true;
	    } else {
	        return false;
	    }
	});	
}

// Form Check Function
function formCheck(obj){

	// 숫자만 입력
	$('.onlyNumbers').bind('keydown',function(event){
	    var keyCode = event.which;
	    
	    // 48-57 Standard Keyboard Numbers
	    var isStandard = (keyCode > 47 && keyCode < 58);

	    // 96-105 Extended Keyboard Numbers (aka Keypad)
	    var isExtended = (keyCode > 95 && keyCode < 106);

	    var validKeyCodes = ',9,8,37,38,39,40,46,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    //console.log("keyCode="+keyCode);
	    
	    if ( isStandard || isExtended || isOther ){
	        return true;
	    } else {
	        return false;
	    }

	}).bind('blur',function(){

	    var pattern = new RegExp('[^0-9]+', 'g');

	    var $input = $(this);
	    var value = $input.val();

	    value = value.replace(pattern, '');
	    $input.val( value );
	});

	// 숫자와 특수문자 입력
	$('.onlyCriteria').bind('keypress',function(event){
	    var keyCode = event.which;
	    //console.log(keyCode);
	    // 48-57 Standard Keyboard Numbers
	    var isStandard = (keyCode > 47 && keyCode < 58);

	    var validKeyCodes = ',8,9,37,38,39,40,46,60,61,62,88,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    if ( isStandard || isOther ){
	        return true;
	    } else {
	        return false;
	    }

	}).bind('blur',function(){

	    var pattern = new RegExp('[^0-9,=,<,>,X,&]+', 'g');
	    //console.log(pattern);

	    var $input = $(this);
	    var value = $input.val();

	    value = value.replace(pattern, '');
	    $input.val( value );
	});

	// 대문자만 입력
	$('.onlyCapital').bind('keypress',function(event){
	    var keyCode = event.which;

	    // 48-57 Standard Keyboard Numbers
	    var isStandard = (keyCode > 64 && keyCode < 91);

	    // 8 Backspace, 46 Forward Delete
	    // 37 Left Arrow, 38 Up Arrow, 39 Right Arrow, 40 Down Arrow
	    var validKeyCodes = ',8,9,37,38,39,40,46,';
	    var isOther = ( validKeyCodes.indexOf(',' + keyCode + ',') > -1 );

	    if ( isStandard || isOther ){
	        return true;
	    } else {
	        return false;
	    }

	}).bind('blur',function(){
	    var pattern = new RegExp('[^A-Z]+', 'g');

	    var $input = $(this);
	    var value = $input.val();

	    value = value.replace(pattern, '');
	    $input.val( value );
	});		
	
	obj.validate({
		//submitHandler: callbackSubmit
		///*
		submitHandler: function(f) {
			//$("div.error").hide();
			//alert("submit! use link below to go to the other step");
			callbackSubmit();
		}
		//*/	
		/*
	    invalidHandler: function(form, validator) {
	    	
	    	console.log(validator);
	    	
	        var errors = validator.numberOfInvalids();
	        if (errors) {
	          var message = errors == 1
	            ? 'You missed 1 field. It has been highlighted'
	            : 'You missed ' + errors + ' fields. They have been highlighted';
	          $("div.error span").html(message);
	          $("div.error").show();
	          alert(message);
	        } else {
	          $("div.error").hide();
	        }
	      }			
		*/
	});	
	
}

/******************************
*  Empty 및 공백 처리
*  param : field, error_msg
*  return : boolean
*******************************/
function isEmptyJq(obj, error_msg){
    if(obj.val() == ''){
        alert(error_msg);
        obj.focus();
        return false;
    }else{
    	return true;
    }
}

// get array max 
Array.prototype.max = function() {
	var max = this[0];
	var max_index = 1;
	var len = this.length;
	for (var i = 1; i < len; i++){ 
		if (this[i] >= max){ 
			max_index = i + 1; 
			max = this[i]; 
			//console.log("max="+i);
		}
	}
	return max_index;
};

//get array min 
Array.prototype.min = function() {
	var min = this[0];
	var min_index = 1;
	var len = this.length;
	for (var i = 1; i < len; i++){ 
		if (this[i] < min){ 
			min_index = i + 1; 
			min = this[i]; 
			//console.log("min="+i);
		}
	}	
	return min_index;
};

function getNumberOnly(str){
    var val = str;
    val = new String(val);
    var regex = /[^0-9]/g;
    val = val.replace(regex, '');
    
    return val;
}

/**
기능	: 새창 정중앙으로 open (href, window_name, width, heigth, scroll, center)
RETURN	: NONE
url	    : 이동할 주소
myname	: 팝업 이름
w		: 넓이
h		: 높이
scroll	: (yes, no, auto)
pos		: (right, center, left) center 로선택할것
*/
function newWindow(url, myname, w, h, scroll, pos, resize) {
	var win = null; 
	
	if(pos == "center"){ 
		LeftPosition = (screen.width)?(screen.width-w)/2:100 ; 
		TopPosition = (screen.height)?(screen.height-h)/2:100 ;
	} 
	
	settings = 'width='+w+',height='+h+',top='+TopPosition+',left='+LeftPosition+',scrollbars='+scroll+',resizable='+resize;
	
	
	win = window.open(url,myname,settings); 
	
	if(win.focus){
		win.focus();
	}
} 

String.prototype.cutStrLength = function(len, tail) { 
    if(typeof tail == 'undefined') tail = "..";    
    var str = this;
    if(str == "") str = "&nbsp;";
    var returnStr = "";
    returnStr = str.toString().substring(0, len);
    if (str.toString().length >= len) returnStr += tail;
    return returnStr;
};

//20141024(8자리 형식) -> 2014-10-24(변환)
function convStringToYmd(strYmd){
	var rtn = "";
	
	if(strYmd.length > 8){
		return strYmd;
	}
	
	rtn = strYmd.slice(0, 4)+"-"+strYmd.slice(4, 6)+"-"+strYmd.slice(6,8);
	
	return rtn;
}

//1024(4자리 형식) -> 10:24(변환)
function convStringToHm(strHm){
	var rtn = "";
	
	if(strHm.length > 8){
		return strHm;
	}
	
	rtn = strHm.slice(0, 2)+":"+strHm.slice(2, 4);
	
	return rtn;
}

//20141024134956(14자리 형식) -> 2014-10-24 13:49:56(변환)
function convStringToDate(strDate){
	var rtn = "";
	
	if(strDate.length > 14){
		return strDate;
	}
	
	rtn = strDate.slice(0, 4)+"-"+strDate.slice(4, 6)+"-"+strDate.slice(6,8);
	rtn += " "+strDate.slice(8, 10)+":"+strDate.slice(10, 12)+":"+strDate.slice(12,14);
	
	return rtn;
}

//밀리세컨드를 00:00:00 형태로 변환
function convMilliSecToHMS(intSec) {
	var input = intSec / 1000;
	var hr, min, sec;
	var cday = 86400; //하루
	var chr = 3600; //한시간
	var cmin = 60;  //1분
	var day = parseInt(input/cday);  //1일로 나눈 몫

	hr = parseInt(input%cday/chr);  //1일로 나눈 나머지를 시간으로 나눔
	min = parseInt(input%cday%chr/cmin);  //일과 시간으로 나눈 나머지를 분으로 나눔
	sec = input%cday%chr%cmin; //그 나머지
	
	if(hr < 10) hr = "0"+hr;
	if(min < 10) min = "0"+min;
	if(sec < 10) sec = "0"+sec;
	
	return (hr + ":" + min + ":" + sec).substr(0, 8);
}

//초를 00:00:00 형태로 변환
function convSecToHMS(intSec) {
	var input = intSec;
	var hr, min, sec;
	var cday = 86400; //하루
	var chr = 3600; //한시간
	var cmin = 60;  //1분
	var day = parseInt(input/cday);  //1일로 나눈 몫
	
	if(intSec == 0){
		return "00:00:00";
	}

	hr = parseInt(input%cday/chr);  //1일로 나눈 나머지를 시간으로 나눔
	min = parseInt(input%cday%chr/cmin);  //일과 시간으로 나눈 나머지를 분으로 나눔
	sec = input%cday%chr%cmin; //그 나머지
	
	if(hr < 10) hr = "0"+hr;
	if(min < 10) min = "0"+min;
	if(sec < 10) sec = "0"+sec;
	
	return (hr + ":" + min + ":" + sec).substr(0, 8);
}

//대신증권 - 초를 000:00 형태로 변환
function convSecToMS(intSec) {
	var input = intSec;
	var hr, min, sec;
	var cday = 86400; //하루
	var chr = 3600; //한시간
	var cmin = 60;  //1분
	var day = parseInt(input/cday);  //1일로 나눈 몫

	min = parseInt(input%cday/cmin);  //일과 시간으로 나눈 나머지를 분으로 나눔
	sec = input%cday%chr%cmin; //그 나머지
	
	if(min < 10) min = "0"+min;
	if(sec < 10) sec = "0"+sec;
	
	return (min + ":" + sec).substr(0, 8);
}

//대신증권 : 000:00을 > 초로 환산 + 1 > 000:00으로 다시 만들어서 반환
function convMsToSecToMs(ms, interval){
	var intMM = parseInt(ms.split(":")[0], 10);
	var intSS = parseInt(ms.split(":")[1], 10);
	
	return convSecToMS((intMM*60 + (intSS+interval)));	//1초 증가
}

//현재 일시를 리턴한다 2014-03-12 11:23:11
function getDateTime(){
    var str     = '';
    var Stamp   = new Date();
    var yyyy    = Stamp.getYear();
	var mm, dd;
	var Hours, Mins, Sec;

    if (yyyy < 2000) yyyy = 1900 + yyyy;
	
	mm = (Stamp.getMonth() + 1);
	if(mm < 10) mm = "0" +mm;

	dd = Stamp.getDate();
	if(dd < 10) dd = "0" +dd;

    str = yyyy + "-" + mm  + "-" + dd;

	Hours = Stamp.getHours();
    Mins = Stamp.getMinutes();
	Sec = Stamp.getSeconds();

    if (Hours < 10) Hours = "0" + Hours;
    if (Mins < 10) Mins = "0" + Mins;
	if (Sec < 10) Sec = "0" + Sec;

    str += ' ' + Hours + ":" + Mins + ":" + Sec;

    return str;
}

//현재 시간을 리턴한다 11:23:11
function getTime(){
    var str     = '';
    var Stamp   = new Date();
	var Hours, Mins, Sec;

	Hours = Stamp.getHours();
    Mins = Stamp.getMinutes();
	Sec = Stamp.getSeconds();

    if (Hours < 10) Hours = "0" + Hours;
    if (Mins < 10) Mins = "0" + Mins;
	if (Sec < 10) Sec = "0" + Sec;

    str = Hours + ":" + Mins + ":" + Sec;

    return str;
}

/*
 * currTime : 00:00:00 형식
 * inTime   : 00:00:00 형식
 * 두 시분초의 차이를 00:00:00 형식으로 리턴한다.  
 */
function getFinesseConvHHMMSS(inTime){
	var chh = 3600; //한시간
	var cmm = 60;  //1분
	
	if(inTime == "") return "";
	
	var currTamp = new Date();
	var currHH = currTamp.getHours();
    var currMM = currTamp.getMinutes();
	var currSS = currTamp.getSeconds();
	
	//현재 시간을 초로 환산
	var currTime = (currHH*chh) + (currMM*cmm) + currSS;
	
	var arrInTime = inTime.split(":");
	var inHH = parseInt(arrInTime[0]) + 9;
	var inMM = parseInt(arrInTime[1]);
	var inSS = parseInt(arrInTime[2]);
	
	//넘어온 시간을 초로 환산
	var reTime = (inHH*chh) + (inMM*cmm) + inSS;
	
	return (convSecToHMS((currTime - reTime)));
}

//전화번호 형태로 리턴 010-1111-2222
function getConvTelNum(num){
	if(num == null) return num;
	return num.replace(/(^02.{0}|^01.{1}|[0-9]{3})([0-9]+)([0-9]{4})/,"$1-$2-$3");
}

//자동으로 YYYY-MM-DD 넣어주기
function autoYYYYMMDD(object){
	if (object.value.length == 4){
		object.value = object.value + "-";
	}else if (object.value.length == 7){
		object.value = object.value + "-";
	}
}

//browser 체크
function getBrowserType(){
    var _ua = navigator.userAgent;
    
    //console.log("=====================================");
    //console.log("navigator.appName : "+_ua);
    //console.log("navigator.appName : "+navigator.appName);
    
    /* IE7,8,9,10,11 */
    if (navigator.appName == 'Microsoft Internet Explorer') {
        var rv = -1;
        var trident = _ua.match(/Trident\/(\d.\d)/i);
         
        //ie11에서는 MSIE토큰이 제거되고 rv:11 토큰으로 수정됨 (Trident표기는 유지)
        if(trident != null && trident[1]  == "7.0") return rv = "IE" + 11;
        if(trident != null && trident[1]  == "6.0") return rv = "IE" + 10;
        if(trident != null && trident[1]  == "5.0") return rv = "IE" + 9;
        if(trident != null && trident[1]  == "4.0") return rv = "IE" + 8;
        if(trident == null) return rv = "IE" + 7;
         
        var re = new RegExp("MSIE ([0-9]{1,}[\.0-9]{0,})");
        if (re.exec(_ua) != null) rv = parseFloat(RegExp.$1);
        return rv;
    }
     
    /* etc */
    var agt = _ua.toLowerCase();
    if (agt.indexOf("chrome") != -1) return 'Chrome';
    if (agt.indexOf("opera") != -1) return 'Opera'; 
    if (agt.indexOf("staroffice") != -1) return 'Star Office'; 
    if (agt.indexOf("webtv") != -1) return 'WebTV'; 
    if (agt.indexOf("beonex") != -1) return 'Beonex'; 
    if (agt.indexOf("chimera") != -1) return 'Chimera'; 
    if (agt.indexOf("netpositive") != -1) return 'NetPositive'; 
    if (agt.indexOf("phoenix") != -1) return 'Phoenix'; 
    if (agt.indexOf("firefox") != -1) return 'Firefox'; 
    if (agt.indexOf("safari") != -1) return 'Safari'; 
    if (agt.indexOf("skipstone") != -1) return 'SkipStone'; 
    if (agt.indexOf("netscape") != -1) return 'Netscape'; 
    if (agt.indexOf("mozilla/5.0") != -1) return 'Mozilla';
}

if (typeof String.prototype.startsWith != 'function') {
  String.prototype.startsWith = function (str){
	return this.slice(0, str.length) == str;
  };
}
String.prototype.endsWith = function(suffix) {
	return this.indexOf(suffix, this.length - suffix.length) !== -1;
};

///////////////////////////////////////
//배열 중복 체크
Array.prototype.valueIndex = function(pval) {
    var idx = -1;
    if(this==null || this==undefined || pval==null || pval==undefined){
    }else{
        for(var i=0;i<this.length;i++){
            if(this[i]==pval){
                idx = i;
                break;
            }
        }
    }
    return idx;
};

Array.prototype.removeDup = function(){
    var resultArray = [];
    if(this==null || this==undefined){
    }else{
        for(var i=0;i<this.length;i++){
            var el = this[i];
            if(resultArray.valueIndex(el) === -1) resultArray.push(el);
            }
        }
    return resultArray;
};
///////////////////////////////////////

/*
 * 해당 년월에 맨 마지막 날짜를 구한다.
 */
function lastDay(year, month){
	var lastDate = new Array(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);
	var test = "";
	
	//윤년검사
	if((0 == year%4 && 0 != year%100) || 0 == year%400){
		lastDate[1] = 29;	//윤년이면 2월의 마지막 날짜는 29일
	}
	
	day = lastDate[month -1];
	
	return day;
}

/*++
매치되는 모든 문자열을 치환한다.
@name  replaceAll
@param  str 문자열
@param  out 치환의 대상 문자
@param  add 치환의 목적 문자
@return 치환 후 문자열
*/
function replaceAll(str, out, add) {
	return str.split(out).join(add);
}

function addNbspHTML(no){
	var rtn = "";
	for(var i=0; i<no; i++){
		rtn += "&nbsp;&nbsp;&nbsp;&nbsp;";
	}
	
	return rtn;
}

//사용법 decode64(base64EncStr)
function decode64(input) {
    var output = "";
    var chr1, chr2, chr3;
    var enc1, enc2, enc3, enc4;
    var i = 0;
    input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");
    while (i < input.length) {
        enc1 = this._keyStr.indexOf(input.charAt(i++));
        enc2 = this._keyStr.indexOf(input.charAt(i++));
        enc3 = this._keyStr.indexOf(input.charAt(i++));
        enc4 = this._keyStr.indexOf(input.charAt(i++));
        chr1 = (enc1 << 2) | (enc2 >> 4);
        chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
        chr3 = ((enc3 & 3) << 6) | enc4;
        output = output + String.fromCharCode(chr1);
        if (enc3 != 64) {
            output = output + String.fromCharCode(chr2);
        }
        if (enc4 != 64) {
            output = output + String.fromCharCode(chr3);
        }
    }
    output = utf8_decode(output);
    return output;
}

function utf8_decode(utftext) {
    var string = "";
    var i = 0;
    var c = c1 = c2 = 0;
    while (i < utftext.length) {
        c = utftext.charCodeAt(i);
        if (c < 128) {
            string += String.fromCharCode(c);
            i++;
        }
        else if ((c > 191) && (c < 224)) {
            c2 = utftext.charCodeAt(i + 1);
            string += String.fromCharCode(((c & 31) << 6) | (c2 & 63));
            i += 2;
        }
        else {
            c2 = utftext.charCodeAt(i + 1);
            c3 = utftext.charCodeAt(i + 2);
            string += String.fromCharCode(((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63));
            i += 3;
        }
    }
    return string;
}

//사용법 encode64(str)
function encode64(input) {
    var output = "";
    var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
    var i = 0;
    input = utf8_encode(input);
    while (i < input.length) {
        chr1 = input.charCodeAt(i++);
        chr2 = input.charCodeAt(i++);
        chr3 = input.charCodeAt(i++);
        enc1 = chr1 >> 2;
        enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
        enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
        enc4 = chr3 & 63;
        if (isNaN(chr2)) {
            enc3 = enc4 = 64;
        } else if (isNaN(chr3)) {
            enc4 = 64;
        }
        output = output +
		this._keyStr.charAt(enc1) + this._keyStr.charAt(enc2) +
		this._keyStr.charAt(enc3) + this._keyStr.charAt(enc4);
    }
    return output;
}

function utf8_encode(string) {
    string = string.replace(/\r\n/g, "\n");
    var utftext = "";
    for (var n = 0; n < string.length; n++) {
        var c = string.charCodeAt(n);
        if (c < 128) {
            utftext += String.fromCharCode(c);
        } else if ((c > 127) && (c < 2048)) {
            utftext += String.fromCharCode((c >> 6) | 192);
            utftext += String.fromCharCode((c & 63) | 128);
        } else {
            utftext += String.fromCharCode((c >> 12) | 224);
            utftext += String.fromCharCode(((c >> 6) & 63) | 128);
            utftext += String.fromCharCode((c & 63) | 128);
        }
    }
    return utftext;
}

function convStrToHtml(str){
	str = str.replace(/</g, "&lt;");
	str = str.replace(/>/g, "&gt;");$
	str = str.replace(/\"/g, "&quot;");
	str = str.replace(/\'/g, "&#39;");
	str = str.replace(/\\n/g, "<br/>");
	return str;
}

function getContextPath(){
    var offset=location.href.indexOf(location.host)+location.host.length;
    var ctxPath=location.href.substring(offset,location.href.indexOf('/',offset+1));
    return ctxPath;
}

/***** seo, jeungdeok 추가 2017.12.21 *****/
//null check
$.notnull=function(obj, strName){
    if(!obj.val()||obj.val()==''||obj.val()==null){
		alert(strName+'(은)는 필수 입력 입니다');
		obj.focus();
		return false;
    }
    return true;
}

//숫자만 입력받도록 체크
$.number=function(obj, strName){
	if(obj.val().length<1) return true;
	for(var pnl=0;pnl<obj.val().length;pnl++){
		if(obj.val().charCodeAt(pnl)<48 || obj.val().charCodeAt(pnl)>57){
			alert(strName+'는 숫자만 입력할 수 있습니다');
			obj.focus();
			return false;
		}
	}
	return true;
};

//공통코드 셀렉트박스(table : tb_comm_code)
$.createCommCodeSelectOption=function(idSelect, cd_class, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createCodeDtlSelectOption.json';
	
    $.post(
		url,
		{'cd_class':cd_class,'selected':selected},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

//디바이스정보 셀렉트박스(table : tb_device_info)
$.createDeviceInfoSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createDeviceInfoSelectOption.json';
    
    $.post(
		url,
		{'cd_class':cd_class,'selected':selected},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};


//디바이스모델정보 셀렉트박스(table : tb_device_model)
$.createDeviceModelSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createDeviceModelSelectOption.json';
    
    $.post(
		url,
		{'selected':selected},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

//CM정보 셀렉트박스(table : tb_config)
$.createCmInfoSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createCmInfoSelectOption.json';
    
    $.post(
		url,
		{ 
		  'selected':selected, 
		  'config_code':'000',
	      'config_class':'CM01',
	      'use_yn':'Y'
		},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

//css 셀렉트박스(table : tb_css)
$.createCssSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createCssSelectOption.json';
    
    $.post(
		url,
		{ 
		  'selected':selected
		},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

//Device Pool 셀렉트박스(table : tb_device_pool)
$.createDevicePoolSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createDevicePoolSelectOption.json';
    
    $.post(
		url,
		{ 
		  'selected':selected
		},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

//Button Template 셀렉트박스(table : tb_btn_template)
$.createBtnTemplateSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createBtnTemplateSelectOption.json';
    
    $.post(
		url,
		{ 
		  'selected':selected
		},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

//location 셀렉트박스(table : tb_location)
$.createLocationSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createLocationSelectOption.json';
    
	$.post(
		url,
		{ 
		  'selected':selected
		},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

//Route Partition 셀렉트박스(table : tb_route_partition)
$.createRoutePartitionSelectOption=function(idSelect, selected, first){
    //$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/createRoutePartitionSelectOption.json';
    
    $.post(
		url,
		{ 
		  'selected':selected
		},
		function(data,textStatus,jqXHR){
		    $('#'+idSelect+" option").remove();
		    if(first != "")
		    	$('#'+idSelect).append("<option value=''>"+first+"</option>");
		    $('#'+idSelect).append(data.html);
		},
		'json'
    ); // $.post(
};

$.createCommonPhoneProfileSelectOption=function(idSelect, selected, first){
	//$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/selectCommonPhoneProfile.json';
    
	$.post(
		url,
		{ 
			'selected':selected
		},
		function(data,textStatus,jqXHR){
			$('#'+idSelect+" option").remove();
			if(first != "")
				$('#'+idSelect).append("<option value=''>"+first+"</option>");
			$('#'+idSelect).append(data.html);
		},
		'json'
	); // $.post(
};
$.createDeviceSecurityProfileSelectOption=function(idSelect, selected, first){
	//$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/selectDeviceSecurityProfile.json';
	
	$.post(
		url,
		{ 
			'selected':selected
		},
		function(data,textStatus,jqXHR){
			$('#'+idSelect+" option").remove();
			if(first != "")
				$('#'+idSelect).append("<option value=''>"+first+"</option>");
			$('#'+idSelect).append(data.html);
		},
		'json'
	); // $.post(
};
$.createMtpCodeSelectOption=function(idSelect, selected, first){
	//$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/selectMtpCode.json';
    
	$.post(
		url,
		{ 
			'selected':selected
		},
		function(data,textStatus,jqXHR){
			$('#'+idSelect+" option").remove();
			if(first != "")
				$('#'+idSelect).append("<option value=''>"+first+"</option>");
			$('#'+idSelect).append(data.html);
		},
		'json'
	); // $.post(
};
$.createSipProfileOption=function(idSelect, selected, first){
	//$.ajaxSetup({async: false});
	var url = "";
    url = getContextPath() + '/common/selectSipProfile.json';
    
	$.post(
		url,
		{ 
			'selected':selected
		},
		function(data,textStatus,jqXHR){
			$('#'+idSelect+" option").remove();
			if(first != "")
				$('#'+idSelect).append("<option value=''>"+first+"</option>");
			$('#'+idSelect).append(data.html);
		},
		'json'
	); // $.post(
};

//입력폼배열의 필드들을 enable 로 설정
$.enableField=function(form_fields){
    $.each(form_fields, function(i, field){
		if($("#"+field).is('input')){
		    $("#"+field).removeAttr('readonly');
		}else if($("#"+field).is('select')){
		    $("#"+field).removeAttr('disabled');
		}else if($("#"+field).is('checkbox')){
		    $("#"+field).removeAttr('disabled');
		}
		
    });
};
//입력폼배열의 필드들을 disable 로 설정
$.disableField=function(form_fields){
    $.each(form_fields, function(i, field){
		if($("#"+field).is('input')){
		    $("#"+field).attr('readonly', true);
		}else if($("#"+field).is('select')){
		    $("#"+field).attr('disabled', 'disabled');
		}else if($("#"+field).is('checkbox')){
		    $("#"+field).attr('disabled', 'disabled');
		}		
    });
};
//입력폼의 필드들을 초기화
$.resetField=function(form_fields){
    $.enableField(form_fields);
    $.each(form_fields, function(i, field){
		$("#"+field).val('');
		if($("#"+field).is('select')){
		    //console.log(field);
		    //console.log($("#"+field+" option:eq(0)").val());
		    $("#"+field).val($("#"+field+" option:eq(0)").val());
		}		
    });
};

//Table의 입력폼 필드들을 enable 로 설정
$.enableTrField=function(formTableTrs){
	formTableTrs.each(function(){
		$(this).find('input[type=text]').eq(0).removeAttr('readonly');
		$(this).find('select').eq(0).removeAttr('disabled');
		$(this).find('input[type=checkbox]').removeAttr('disabled');
		$(this).find('input[type=radio]').each(function(){
			$(this).removeAttr('disabled', 'disabled');
		});		
    }); 
};
//Table의 입력폼 필드들을 disable 로 설정
$.disableTrField=function(formTableTrs){
	formTableTrs.each(function(){
		$(this).find('input[type=text]').eq(0).attr('readonly', true);
		$(this).find('select').eq(0).attr('disabled', 'disabled');
		$(this).find('input[type=checkbox]').eq(0).attr('disabled', 'disabled');
		$(this).find('input[type=radio]').each(function(){
			$(this).attr('disabled', 'disabled');
		});		
    }); 
};
//Table의 입력폼 필드들을 초기화
$.resetTrField=function(formTableTrs){
	$.enableTrField(formTableTrs);
	formTableTrs.each(function(){
		$(this).find('input[type=text]').eq(0).val('');
		$(this).find('select').eq(0).val($(this).children("option:eq(0)").val());
		//$(this).find('input[type=checkbox]').eq(0).prop('checked', true);
		$(this).find('input[type=radio]').eq(0).prop("checked", true);
    }); 
};


$.getYear=function(){
    var year = new Date().getFullYear();    
    return year;
}

$.getMonth=function(){
    var month = new Date().getMonth()+1; //January is 0!
    if(month<10) { month='0'+month	} 
    return month;
}

$.getDay=function(){
    var day = new Date().getDate()
    if(day<10) { day='0'+day	} 
    return day;
}

$.lastDay=function (year, month){
    var lastDate = new Array(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);
    //윤년검사
    if((0 == year%4 && 0 != year%100) || 0 == year%400){
	lastDate[1] = 29;	//윤년이면 2월의 마지막 날짜는 29일
    }	
    day = lastDate[month -1];	
    return day;
}

