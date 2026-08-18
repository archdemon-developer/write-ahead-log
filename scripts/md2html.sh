#!/bin/bash

# Proper Markdown to HTML Converter (Pure Bash)
# Handles: headers, bold, italic, code, lists, tables, links, paragraphs

convert_markdown_to_html() {
    local input="$1"
    local in_table=false
    local in_list=false
    local in_code_block=false
    local code_lang=""

    echo "$input" | while IFS= read -r line; do
        # Code blocks
        if [[ "$line" =~ ^\`\`\` ]]; then
            if [ "$in_code_block" = false ]; then
                in_code_block=true
                code_lang=$(echo "$line" | sed 's/^```//')
                echo "<pre><code class=\"language-$code_lang\">"
            else
                in_code_block=false
                echo "</code></pre>"
            fi
            continue
        fi

        if [ "$in_code_block" = true ]; then
            # Escape HTML in code blocks
            line=$(echo "$line" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
            echo "$line"
            continue
        fi

        # Headers
        if [[ "$line" =~ ^#\ (.*) ]]; then
            echo "<h1>${BASH_REMATCH[1]}</h1>"
            continue
        fi
        if [[ "$line" =~ ^##\ (.*) ]]; then
            echo "<h2>${BASH_REMATCH[1]}</h2>"
            continue
        fi
        if [[ "$line" =~ ^###\ (.*) ]]; then
            echo "<h3>${BASH_REMATCH[1]}</h3>"
            continue
        fi
        if [[ "$line" =~ ^####\ (.*) ]]; then
            echo "<h4>${BASH_REMATCH[1]}</h4>"
            continue
        fi

        # Horizontal rules
        if [[ "$line" =~ ^---+$ ]]; then
            echo "<hr />"
            continue
        fi

        # Table detection (starts with |)
        if [[ "$line" =~ ^\| ]]; then
            if [ "$in_table" = false ]; then
                in_table=true
                echo "<table class=\"metrics-table\">"
                echo "<thead>"
            fi

            # Parse table row
            local cells=()
            local is_header=false

            # Check if next line is separator (indicates header)
            if [[ "$line" =~ -+\| ]]; then
                is_header=true
            fi

            # Extract cells
            line="${line#|}"  # Remove leading |
            line="${line%|}"  # Remove trailing |

            if [ "$is_header" = true ]; then
                echo "</thead><tbody>"
                is_header=false
            else
                echo "<tr>"
                while IFS='|' read -r cell; do
                    cell=$(echo "$cell" | xargs)  # Trim whitespace
                    # Inline formatting
                    cell=$(echo "$cell" | sed 's/\*\*\([^*]*\)\*\*/<strong>\1<\/strong>/g')
                    cell=$(echo "$cell" | sed 's/\*\([^*]*\)\*/<em>\1<\/em>/g')
                    cell=$(echo "$cell" | sed 's/`\([^`]*\)`/<code>\1<\/code>/g')
                    echo "<td>$cell</td>"
                done <<< "$(echo "$line" | sed 's/|/\n/g')"
                echo "</tr>"
            fi
            continue
        elif [ "$in_table" = true ]; then
            in_table=false
            echo "</tbody></table>"
        fi

        # Lists
        if [[ "$line" =~ ^-\ (.*) ]]; then
            if [ "$in_list" = false ]; then
                in_list=true
                echo "<ul>"
            fi
            echo "<li>${BASH_REMATCH[1]}</li>"
            continue
        elif [ "$in_list" = true ] && [[ ! "$line" =~ ^-\ ]]; then
            in_list=false
            echo "</ul>"
        fi

        # Empty lines
        if [ -z "$line" ]; then
            if [ "$in_list" = true ]; then
                in_list=false
                echo "</ul>"
            fi
            echo ""
            continue
        fi

        # Apply inline formatting
        line=$(echo "$line" | sed 's/\*\*\([^*]*\)\*\*/<strong>\1<\/strong>/g')
        line=$(echo "$line" | sed 's/\*\([^*]*\)\*/<em>\1<\/em>/g')
        line=$(echo "$line" | sed 's/`\([^`]*\)`/<code>\1<\/code>/g')

        # Regular paragraphs
        if [[ ! "$line" =~ ^\| ]]; then
            echo "<p>$line</p>"
        fi
    done

    # Close any open tags
    if [ "$in_list" = true ]; then
        echo "</ul>"
    fi
    if [ "$in_table" = true ]; then
        echo "</tbody></table>"
    fi
}

# Main execution
if [ $# -eq 0 ]; then
    # Read from stdin
    input=$(cat)
    convert_markdown_to_html "$input"
else
    # Read from file
    input=$(cat "$1")
    convert_markdown_to_html "$input"
fi