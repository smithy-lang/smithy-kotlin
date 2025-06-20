<#--
    This is an unmodified version of Dokka's source_set_selector.ftl
    https://github.com/Kotlin/dokka/blob/83b0f8ad9ad920df0d842caa9c43d69e6e2c44f6/dokka-subprojects/plugin-base/src/main/resources/dokka/templates/includes/source_set_selector.ftl
-->
<#macro display>
    <#if sourceSets?has_content>
        <ul class="filter-section filter-section_loading" id="filter-section" aria-label="Platform filter">
            <#list sourceSets as ss>
                <button class="platform-tag platform-selector ${ss.platform}-like" data-active="" aria-pressed="true"
                        data-filter="${ss.filter}">${ss.name}</button>
            </#list>
            <div class="dropdown filter-section--dropdown" data-role="dropdown" id="filter-section-dropdown">
                <button class="button button_dropdown filter-section--dropdown-toggle" role="combobox"
                        data-role="dropdown-toggle"
                        aria-controls="platform-tags-listbox"
                        aria-haspopup="listbox"
                        aria-expanded="false"
                        aria-label="Toggle source sets"
                ></button>
                <ul role="listbox" id="platform-tags-listbox" class="dropdown--list" data-role="dropdown-listbox" aria-label="Platform filter">
                    <div class="dropdown--header"><span>Platform filter</span>
                        <button class="button" data-role="dropdown-toggle" aria-label="Close platform filter">
                            <i class="ui-kit-icon ui-kit-icon_cross"></i>
                        </button>
                    </div>
                    <#list sourceSets as ss>
                        <li role="option" class="dropdown--option platform-selector-option ${ss.platform}-like" tabindex="0">
                            <label class="checkbox">
                                <input type="checkbox" class="checkbox--input" id="${ss.filter}"
                                       data-filter="${ss.filter}"/>
                                <span class="checkbox--icon"></span>
                                ${ss.name}
                            </label>
                        </li>
                    </#list>
                </ul>
                <div class="dropdown--overlay"></div>
            </div>
        </ul>
    </#if>
</#macro>