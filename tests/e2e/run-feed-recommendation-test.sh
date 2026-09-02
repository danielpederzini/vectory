#!/usr/bin/env bash

set -euo pipefail

readonly E2E_COMPOSE_PROJECT_NAME="vectory-feed-e2e-$$"
readonly COMPOSE_BASE_FILE="docker-compose.yml"
readonly COMPOSE_E2E_FILE="tests/e2e/docker-compose.feed-recommendation.yml"
readonly CONTENT_MANAGER_BASE_URL="http://localhost:18080"
readonly USER_MANAGER_BASE_URL="http://localhost:18081"
readonly RECOMMENDATION_MANAGER_BASE_URL="http://localhost:18082"
readonly MAXIMUM_WAIT_SECONDS=90
readonly POLL_INTERVAL_SECONDS=1

compose_command() {
    docker compose --project-name "$E2E_COMPOSE_PROJECT_NAME" \
        --file "$COMPOSE_BASE_FILE" --file "$COMPOSE_E2E_FILE" "$@"
}

cleanup_e2e_stack() {
    compose_command down --volumes --remove-orphans
}

wait_for_http_server() {
    local service_name="$1"
    local service_url="$2"
    local elapsed_seconds=0

    until curl --silent --output /dev/null --write-out '%{http_code}' "$service_url" | grep --quiet --extended-regexp '^[1-5][0-9][0-9]$'; do
        if (( elapsed_seconds >= MAXIMUM_WAIT_SECONDS )); then
            echo "Timed out waiting for $service_name at $service_url" >&2
            return 1
        fi
        sleep "$POLL_INTERVAL_SECONDS"
        ((elapsed_seconds += POLL_INTERVAL_SECONDS))
    done
}

create_user() {
    local username="$1"
    local email_address="$2"
    local user_response

    user_response=$(curl --fail-with-body --silent --show-error \
        --request POST "$USER_MANAGER_BASE_URL/api/v1/users/signup" \
        --header 'Content-Type: application/json' \
        --data "{\"username\":\"$username\",\"email\":\"$email_address\",\"password\":\"secure-password\"}")
    jq --raw-output '.id' <<<"$user_response"
}

create_post() {
    local author_id="$1"
    local post_text="$2"
    local post_response

    post_response=$(curl --fail-with-body --silent --show-error \
        --request POST "$CONTENT_MANAGER_BASE_URL/api/v1/posts" \
        --header 'Content-Type: application/json' \
        --data "{\"authorId\":\"$author_id\",\"text\":\"$post_text\"}")
    jq --raw-output '.id' <<<"$post_response"
}

create_like_interaction() {
    local user_id="$1"
    local post_id="$2"

    curl --fail-with-body --silent --show-error \
        --request POST "$CONTENT_MANAGER_BASE_URL/api/v1/interactions" \
        --header 'Content-Type: application/json' \
        --data "{\"userId\":\"$user_id\",\"postId\":\"$post_id\",\"type\":\"LIKE\"}" >/dev/null
}

assert_feed_recommendation() {
    local user_id="$1"
    local expected_recommended_post_id="$2"
    local interacted_post_id="$3"
    local elapsed_seconds=0
    local feed_response

    while (( elapsed_seconds < MAXIMUM_WAIT_SECONDS )); do
        feed_response=$(curl --fail-with-body --silent --show-error \
            "$RECOMMENDATION_MANAGER_BASE_URL/api/v1/recommendations/$user_id?limit=3")

        local first_recommended_post_id
        local includes_interacted_post
        first_recommended_post_id=$(jq --raw-output '.items[0].postId // empty' <<<"$feed_response")
        includes_interacted_post=$(jq --arg interacted_post_id "$interacted_post_id" \
            '[.items[].postId] | index($interacted_post_id) != null' <<<"$feed_response")

        if [[ "$first_recommended_post_id" == "$expected_recommended_post_id" \
            && "$includes_interacted_post" == "false" ]]; then
            echo "Validated feed for user $user_id: $feed_response"
            return 0
        fi

        sleep "$POLL_INTERVAL_SECONDS"
        ((elapsed_seconds += POLL_INTERVAL_SECONDS))
    done

    echo "Feed assertion failed for user $user_id. Expected first post $expected_recommended_post_id and exclusion of $interacted_post_id." >&2
    echo "Last feed response: $feed_response" >&2
    return 1
}

main() {
    trap cleanup_e2e_stack EXIT

    CONTENT_MANAGER_PORT=18080 \
    USER_MANAGER_PORT=18081 \
    RECOMMENDATION_MANAGER_PORT=18082 \
    compose_command build user-manager
    CONTENT_MANAGER_PORT=18080 \
    USER_MANAGER_PORT=18081 \
    RECOMMENDATION_MANAGER_PORT=18082 \
    compose_command build content-manager
    CONTENT_MANAGER_PORT=18080 \
    USER_MANAGER_PORT=18081 \
    RECOMMENDATION_MANAGER_PORT=18082 \
    compose_command build recommendation-manager embedding-stub
    CONTENT_MANAGER_PORT=18080 \
    USER_MANAGER_PORT=18081 \
    RECOMMENDATION_MANAGER_PORT=18082 \
    compose_command up --detach

    wait_for_http_server "user-manager" "$USER_MANAGER_BASE_URL/api/v1/users/signup"
    wait_for_http_server "content-manager" "$CONTENT_MANAGER_BASE_URL/api/v1/posts"
    wait_for_http_server "recommendation-manager" "$RECOMMENDATION_MANAGER_BASE_URL/api/v1/recommendations/00000000-0000-0000-0000-000000000000"

    local author_user_id
    local coffee_interest_user_id
    local hiking_interest_user_id
    local coffee_anchor_post_id
    local espresso_recommendation_post_id
    local hiking_anchor_post_id
    local mountain_recommendation_post_id

    author_user_id=$(create_user "e2e-author" "e2e-author@example.com")
    coffee_interest_user_id=$(create_user "e2e-coffee" "e2e-coffee@example.com")
    hiking_interest_user_id=$(create_user "e2e-hiking" "e2e-hiking@example.com")
    coffee_anchor_post_id=$(create_post "$author_user_id" "Coffee brewing basics")
    espresso_recommendation_post_id=$(create_post "$author_user_id" "Espresso extraction techniques")
    hiking_anchor_post_id=$(create_post "$author_user_id" "Hiking preparation essentials")
    mountain_recommendation_post_id=$(create_post "$author_user_id" "Mountain trail navigation")

    create_like_interaction "$coffee_interest_user_id" "$coffee_anchor_post_id"
    create_like_interaction "$hiking_interest_user_id" "$hiking_anchor_post_id"

    assert_feed_recommendation "$coffee_interest_user_id" \
        "$espresso_recommendation_post_id" "$coffee_anchor_post_id"
    assert_feed_recommendation "$hiking_interest_user_id" \
        "$mountain_recommendation_post_id" "$hiking_anchor_post_id"
}

main "$@"
