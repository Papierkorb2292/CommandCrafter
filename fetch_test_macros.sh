repos=$(gh search repos --language mcfunction --updated ">=2025-01-01" --limit 1000 --json fullName --jq .[].fullName)
for repo in ${repos}; do
    echo Querying repo: $repo;

    remaining_rate_limit=$(gh api rate_limit --jq .resources.code_search.remaining)
    # If remaining=1, the next request would error
    if [ $remaining_rate_limit -lt 2 ]; then
        echo Hit rate limit, waiting 60s
        sleep 60s
    fi

    gh search code --language mcfunction --repo $repo --limit 20 $ > tmp_macros
    # Remove leading file path and filter for lines that actually start with $ and don't end with \ because I'm not dealing with multiline right now
    cut -d' ' -f2- tmp_macros | grep -o "^\$.*[^\\]$" >> test_macros.mcfunction
done

rm tmp_macros