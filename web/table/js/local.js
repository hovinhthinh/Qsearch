// Type suggestion
function showSuggestedType(input, listDOM, prefix, suggestedType) {
    // for each item in the array...
    for (i = 0; i < suggestedType.length; i++) {
        //check if the item starts with the same letters as the text field value:
        if (suggestedType[i]['first'].substr(0, prefix.length) == prefix) {
            // create a DIV element for each matching element:
            var b = document.createElement("DIV");
            // make the matching letters bold:*/
            b.innerHTML = "<strong>" + suggestedType[i]['first'].substr(0, prefix.length) + "</strong>";
            b.innerHTML += suggestedType[i]['first'].substr(prefix.length);
            b.innerHTML += ' (' + suggestedType[i]['second'] + ')';
            // insert a input field that will hold the current array item's value:
            b.innerHTML += "<input type='hidden' value='" + suggestedType[i]['first'] + "'>";
            // execute a function when someone clicks on the item value (DIV element):
            b.addEventListener("click", function (e) {
                // insert the value for the autocomplete text field:
                input.value = this.getElementsByTagName("input")[0].value;
                // close the list of autocompleted values,
                //(or any other open lists of autocompleted values:
                closeAllLists();
            });
            listDOM.appendChild(b);
        }
    }
}

var isFetching = false;
var currentFocus = -1;

function autocomplete(inp) {
    // the autocomplete function takes two arguments,
    // the text field element and an array of possible autocompleted values:

    // execute a function when someone writes in the text field:
    inp.addEventListener("input", function (e) {
        if (isFetching) {
            return;
        }
        var val = this.value;
        // close any already open lists of autocompleted values
        closeAllLists();
        if (!val) {
            return false;
        }
        currentFocus = -1;
        //create a DIV element that will contain the items (values):
        var listDOM = document.createElement("DIV");
        listDOM.setAttribute("id", this.id + "-autocomplete-list");
        listDOM.setAttribute("class", "autocomplete-items");
        listDOM.style.width = $(inp).css('width');
        //append the DIV element as a child of the autocomplete container:
        this.parentNode.appendChild(listDOM);

        // get suggestion types from API
        var params = {prefix: val};
        $.ajax({
            type: "GET",
            url: "/type_suggest_table",
            data: params,
            contentType: 'application/json; charset=utf-8',
            dataType: "json",
            beforeSend: function () {
                isFetching = true;
            },
            success: function (data) {
                showSuggestedType(inp, listDOM, data['prefix'], data['suggestions']);
            },
            error: function () {
                // do nothing.
            },
            complete: function () {
                isFetching = false;
            }
        });
    });

    function closeAllLists(elmnt) {
        // close all autocomplete lists in the document, except the one passed as an argument:
        var x = document.getElementsByClassName("autocomplete-items");
        for (var i = 0; i < x.length; i++) {
            if (elmnt != x[i] && elmnt != inp) {
                x[i].parentNode.removeChild(x[i]);
            }
        }
        currentFocus = -1;
    }

    // execute a function presses a key on the keyboard:
    inp.addEventListener("keydown", function (e) {
        var x = document.getElementById(this.id + "-autocomplete-list");
        if (x) x = x.getElementsByTagName("div");
        if (e.keyCode == 40) {
            // If the arrow DOWN key is pressed, increase the currentFocus variable:
            currentFocus++;
            // and and make the current item more visible:
            addActive(x);
        } else if (e.keyCode == 38) {
            // If the arrow UP key is pressed, decrease the currentFocus variable:
            currentFocus--;
            // and and make the current item more visible:
            addActive(x);
        } else if (e.keyCode == 13) {
            // If the ENTER key is pressed, prevent the form from being submitted,
            e.preventDefault();
            if (currentFocus > -1) {
                // and simulate a click on the "active" item:
                if (x) x[currentFocus].click();
                currentFocus = -1;
            } else {
                $("#search").click();
            }
        } else if (e.keyCode == 27) {
            // If the ESC key is pressed
            currentFocus = -1;
            closeAllLists();
        }
    });

    function addActive(x) {
        // a function to classify an item as "active":
        if (!x) return false;
        // start by removing the "active" class on all items:
        removeActive(x);
        if (currentFocus >= x.length) currentFocus = 0;
        if (currentFocus < 0) currentFocus = (x.length - 1);
        // add class "autocomplete-active":
        x[currentFocus].classList.add("autocomplete-active");
    }

    function removeActive(x) {
        // a function to remove the "active" class from all autocomplete items:
        for (var i = 0; i < x.length; i++) {
            x[i].classList.remove("autocomplete-active");
        }
    }

    // execute a function when someone clicks in the document:
    document.addEventListener("click", function (e) {
        closeAllLists(e.target);
    });
}

// settings.
$('#corpus').multiselect({
    templates: {
        button: '<button style="width: 15em" type="button" class="multiselect dropdown-toggle btn btn-default" data-toggle="dropdown"><span class="multiselect-selected-text"></span></button>',
        li: '<li><a><label class="pl-3 pt-1 pb-1" style="color: #495057"></label></a></li>'
    }
});

var showSetting = $.cookie('show_setting_table');
if (showSetting == null) {
    $('#setting-body').removeClass('show');
    $('#setting-toggle').addClass('collapsed');
}
$('#setting-body').on('shown.bs.collapse', function () {
    $.cookie('show_setting', '1');
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
var ntop = $.cookie('ntop_table');
if (ntop != null) {
    $("#ntop option[value=" + ntop + "]").prop('selected', true);
}

$("#corpus").on('change', function () {
    $.cookie('corpus_table', JSON.stringify($(this).val()));
});
$("#ntop").on('change', function () {
    $.cookie('ntop_table', this.value);
});




