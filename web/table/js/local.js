// settings.
$('#corpus').multiselect({
    templates: {
        button: '<button style="width: 10em" type="button" class="multiselect dropdown-toggle btn btn-default" data-toggle="dropdown"><span class="multiselect-selected-text"></span></button>',
        li: '<li><a><label class="pl-3 pt-1 pb-1" style="color: #495057"></label></a></li>'
    }
});

var showSetting = $.cookie('show_setting_table');
if (showSetting == null) {
    $('#setting-body').removeClass('show');
    $('#setting-toggle').addClass('collapsed');
}
$('#setting-body').on('shown.bs.collapse', function () {
    $.cookie('show_setting_table', '1');
});
$('#setting-body').on('hidden.bs.collapse', function () {
    $.removeCookie('show_setting_table');
});

// load saved settings.
var corpus = $.cookie('corpus_table');
if (corpus != null) {
    $('#corpus').multiselect('deselectAll', false);
    $('#corpus').multiselect('select', JSON.parse(corpus));
}
var pgSize = $.cookie('pg_size_table');
if (pgSize != null) {
    $("#pgSize option[value=" + pgSize + "]").prop('selected', true);
}
$("#corpus").on('change', function () {
    $.cookie('corpus_table', JSON.stringify($(this).val()));
});
$("#pgSize").on('change', function () {
    $.cookie('pg_size_table', this.value);
});

// consistency params
$('#consistency-param').on('shown.bs.collapse', function (event) {
    event.stopPropagation();
})
$('#consistency-param').on('hidden.bs.collapse', function (event) {
    event.stopPropagation();
})

function hideParameter() {
    if ($("#rescore").is(':checked')) {
        $('#consistency-param').collapse('show');
    } else {
        $('#consistency-param').collapse('hide');
    }
}

$("#rescore").on('change', function () {
    $.cookie('rescore_table', $(this).is(':checked'));
    hideParameter();
});

var rescore = $.cookie('rescore_table');
if (rescore != null && rescore == 'true') {
    $("#rescore").prop('checked', true);
    $('#consistency-param').addClass('show');
}

["linking-threshold",
    "consistency-param-nfold", "consistency-param-prate", "consistency-param-k", "consistency-param-rho"]
    .forEach(function (o) {
        $("#" + o).on('input', function () {
            $.cookie(o, this.value);
        });

        var value = $.cookie(o);
        if (value != null) {
            $("#" + o).val(value).trigger('input');
        }
    });

// search mode.
// load
if ($.cookie('part_search_table') == "1") {
    $("#type").removeAttr("hidden");
    $("#type").focus();
    $("#context").removeAttr("hidden");
    $("#quantity").removeAttr("hidden");
    $("#full").attr("hidden", "");
}

function changeSearchMode() {
    if ($.cookie('part_search_table') != "1") {
        $("#type").removeAttr("hidden");
        $("#type").focus();
        $("#context").removeAttr("hidden");
        $("#quantity").removeAttr("hidden");
        $("#full").attr("hidden", "");
        $.cookie('part_search_table', "1");
    } else {
        $("#type").attr("hidden", "");
        $("#context").attr("hidden", "");
        $("#quantity").attr("hidden", "");
        $("#full").removeAttr("hidden");
        $("#full").focus();
        $.cookie('part_search_table', "0");
    }
}

$("#change-mode").click(changeSearchMode);

// FIX BOOTSTRAP TOGGLE HEIGHT
$(document).ready(function () {
    $('input[type="checkbox"][data-toggle="toggle"]').parent().css('height', '');
});