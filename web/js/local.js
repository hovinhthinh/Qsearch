// settings.
$('#corpus').multiselect({
    templates: {
        button: '<button style="width: 15em" type="button" class="multiselect dropdown-toggle btn btn-default" data-toggle="dropdown"><span class="multiselect-selected-text"></span></button>',
        li: '<li><a><label class="pl-3 pt-1 pb-1" style="color: #495057"></label></a></li>'
    }
});

var showSetting = $.cookie('show_setting');
if (showSetting == null) {
    $('#setting-body').removeClass('show');
    $('#setting-toggle').addClass('collapsed');
}
$('#setting-body').on('shown.bs.collapse', function () {
    $.cookie('show_setting', '1');
});
$('#setting-body').on('hidden.bs.collapse', function () {
    $.removeCookie('show_setting');
});

// load saved settings.
var corpus = $.cookie('corpus');
if (corpus != null) {
    $('#corpus').multiselect('deselectAll', false);
    $('#corpus').multiselect('select', JSON.parse(corpus));
}
var pgSize = $.cookie('pg_size');
if (pgSize != null) {
    $("#pgSize option[value=" + pgSize + "]").prop('selected', true);
}
var model = $.cookie('model');
if (model != null) {
    $("#model option[value=" + model + "]").prop('selected', true);
}
var lambda = $.cookie('lambda');
if (lambda != null) {
    $("#lambda").val(lambda);
    $("#lambda").trigger('input');
}
var alpha = $.cookie('alpha');
if (alpha != null) {
    $("#alpha").val(alpha);
    $("#alpha").trigger('input');
}
$("#lambda").on('input', function () {
    $.cookie('lambda', this.value);
});
$("#alpha").on('input', function () {
    $.cookie('alpha', this.value);
});

function hideParameter() {
    if ($("#model").val() == 'EMBEDDING') {
        $('#lambda-option').attr('hidden', 'hidden');
        $('#alpha-option').removeAttr('hidden');
    } else {
        $('#alpha-option').attr('hidden', 'hidden');
        $('#lambda-option').removeAttr('hidden');
    }
}

hideParameter();
$("#corpus").on('change', function () {
    $.cookie('corpus', JSON.stringify($(this).val()));
});
$("#pgSize").on('change', function () {
    $.cookie('pg_size', this.value);
});
$("#model").on('change', function () {
    $.cookie('model', this.value);
    hideParameter();
});

// search mode.
// load
if ($.cookie('part_search') == "1") {
    $("#type").removeAttr("hidden");
    $("#type").focus();
    $("#context").removeAttr("hidden");
    $("#quantity").removeAttr("hidden");
    $("#full").attr("hidden", "");
}

function changeSearchMode() {
    if ($.cookie('part_search') != "1") {
        $("#type").removeAttr("hidden");
        $("#type").focus();
        $("#context").removeAttr("hidden");
        $("#quantity").removeAttr("hidden");
        $("#full").attr("hidden", "");
        $.cookie('part_search', "1");
    } else {
        $("#type").attr("hidden", "");
        $("#context").attr("hidden", "");
        $("#quantity").attr("hidden", "");
        $("#full").removeAttr("hidden");
        $("#full").focus();
        $.cookie('part_search', "0");
    }
}

$("#change-mode").click(changeSearchMode);


