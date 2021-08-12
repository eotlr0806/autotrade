$.initLogin=function(){		
	//$('#user_id').val($.cookie('user_id'));
	//if($('#user_id').val()) $('#user_pw').focus();
	//else $('#user_id"]').focus();
	
	$('#user_id').maxlength(10);
	$('#user_pw').maxlength(20);
	$('body').keypress(function(e){
		if(e.keyCode!=13) return;
		if(!$('#user_id').val()) $('#user_id').focus();
		if(!$('#user_pw').val()) $('#user_pw').focus();
		clickBtnLogin(); 
	});	
}; // $.initLogin=function(){


$.alertLoginError=function(){
	alert('접속정보를 확인하시기 바랍니다.');
	$('input[name="user_id"]').val('');
	$('input[name="user_pw"]').val('');
}; // $.alertLoginError=function(){


$('#btnLogin').click(clickBtnLogin=function(){
	if(!$('#user_id').val()) {
		alert("ID를 입력하세요.");
		$('#user_id').focus();
		return false;
	}
	if(!$('#user_pw').val()) {
		alert("패스워드를 입력하세요.");
		$('#user_pw').focus();
		return false;
	}
	
	var data = 'user_id='+encodeURIComponent($('#user_id').val());
	data += '&user_pw='+encodeURIComponent($('#user_pw').val());
	data += '&lang='+encodeURIComponent($('#lang').val());
	
	$.ajax({
		url:'/loginProc.json',
		data:data,
		dataType:'json',
		success:function(data){
			//alert(data.success);
			if(!data){
				$.alertLoginError();
				return false;
			}
			if(data.msg) alert(data.msg);
			if(data.success==0||data.success==3){	//ID 틀림 or 오류발생
				$('#user_id').val('');
				$('#user_pw').val('');
				$('#user_id').focus();
				return false;
			}else if(data.success==2){//PW틀림
				$('#user_pw').val('');
				$('#user_pw').focus();
				return false;
			}
			
			//location.href='/index.do';		
			location.href='/';		
		},
		error:function(xhr, ajaxOptions, thrownError){
			alert(thrownError);
			$.alertLoginError();
			return false;
		}
	}); // $.ajax
}); // $('#btnLogin').click(clickBtnLogin=function(){



$('#btnLogout').click(clickBtnLogout=function(){
	$.post(
		'/logout.json',
		function(data,textStatus,jqXHR){
			if(!data){ alert('return data error'); return false; }
			if(data.msg) alert(data.msg);
			if(!data.success) return;
			location.href='/';
		},
		'json'
	); // $.post(
});	// $('#btnLogout').click(clickBtnLogin=function(){


