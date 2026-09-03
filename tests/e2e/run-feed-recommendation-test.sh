#!/usr/bin/env bash

set -euo pipefail

readonly E2E_COMPOSE_PROJECT_NAME="${E2E_COMPOSE_PROJECT_NAME_OVERRIDE:-vectory-feed-e2e-$$}"
readonly COMPOSE_BASE_FILE="docker-compose.yml"
readonly COMPOSE_E2E_FILE="tests/e2e/docker-compose.feed-recommendation.yml"
readonly CONTENT_MANAGER_BASE_URL="http://localhost:18080"
readonly USER_MANAGER_BASE_URL="http://localhost:18081"
readonly RECOMMENDATION_MANAGER_BASE_URL="http://localhost:18082"
readonly MAXIMUM_WAIT_SECONDS=180
readonly POLL_INTERVAL_SECONDS=1
readonly POSTS_PER_USER=6
readonly USERS_PER_TOPIC=3
readonly TOP_FIVE_RECOMMENDATIONS=5

readonly -a TOPIC_NAMES=(coffee hiking photography cooking)

declare -A USER_ID_BY_TOPIC_AND_MEMBER
declare -A USER_TOPIC_BY_ID
declare -A POST_ID_BY_TOPIC_AND_SEQUENCE
declare -A POST_TOPIC_BY_ID
declare -A INTERACTED_POST_IDS_BY_USER

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

create_interaction() {
    local user_id="$1"
    local post_id="$2"
    local interaction_type="$3"

    curl --fail-with-body --silent --show-error \
        --request POST "$CONTENT_MANAGER_BASE_URL/api/v1/interactions" \
        --header 'Content-Type: application/json' \
        --data "{\"userId\":\"$user_id\",\"postId\":\"$post_id\",\"type\":\"$interaction_type\"}" >/dev/null
    INTERACTED_POST_IDS_BY_USER["$user_id"]+="${post_id},"
}

create_social_users() {
    local topic_name
    local member_number
    local username
    local email_address
    local user_id

    for topic_name in "${TOPIC_NAMES[@]}"; do
        for member_number in $(seq 1 "$USERS_PER_TOPIC"); do
            username="e2e-${topic_name}-${member_number}"
            email_address="${username}@example.com"
            user_id=$(create_user "$username" "$email_address")
            USER_ID_BY_TOPIC_AND_MEMBER["${topic_name}:${member_number}"]="$user_id"
            USER_TOPIC_BY_ID["$user_id"]="$topic_name"
            INTERACTED_POST_IDS_BY_USER["$user_id"]=""
        done
    done
}

create_community_posts() {
    local topic_name
    local member_number
    local post_number
    local post_sequence
    local author_user_id
    local post_text
    local post_id

    for topic_name in "${TOPIC_NAMES[@]}"; do
        post_sequence=1
        for member_number in $(seq 1 "$USERS_PER_TOPIC"); do
            author_user_id="${USER_ID_BY_TOPIC_AND_MEMBER["${topic_name}:${member_number}"]}"
            for post_number in $(seq 1 "$POSTS_PER_USER"); do
                post_text="[E2E_TOPIC:${topic_name}] ${topic_name} community post ${post_sequence} by member ${member_number}"
                post_id=$(create_post "$author_user_id" "$post_text")
                POST_ID_BY_TOPIC_AND_SEQUENCE["${topic_name}:${post_sequence}"]="$post_id"
                POST_TOPIC_BY_ID["$post_id"]="$topic_name"
                ((post_sequence += 1))
            done
        done
    done
}

get_post_id() {
    local topic_name="$1"
    local post_sequence="$2"
    echo "${POST_ID_BY_TOPIC_AND_SEQUENCE["${topic_name}:${post_sequence}"]}"
}

get_adjacent_topic_name() {
    local topic_name="$1"
    case "$topic_name" in
        coffee) echo hiking ;;
        hiking) echo photography ;;
        photography) echo cooking ;;
        cooking) echo coffee ;;
    esac
}

create_view_interaction_wave() {
    local topic_name
    local member_number
    local user_id
    local first_post_sequence
    local second_post_sequence

    for topic_name in "${TOPIC_NAMES[@]}"; do
        for member_number in $(seq 1 "$USERS_PER_TOPIC"); do
            user_id="${USER_ID_BY_TOPIC_AND_MEMBER["${topic_name}:${member_number}"]}"
            first_post_sequence="$member_number"
            second_post_sequence=$((member_number + 3))
            create_interaction "$user_id" "$(get_post_id "$topic_name" "$first_post_sequence")" VIEW
            create_interaction "$user_id" "$(get_post_id "$topic_name" "$second_post_sequence")" VIEW
        done
    done
}

create_like_and_save_interaction_wave() {
    local topic_name
    local member_number
    local user_id
    local liked_post_sequence
    local saved_post_sequence

    for topic_name in "${TOPIC_NAMES[@]}"; do
        for member_number in $(seq 1 "$USERS_PER_TOPIC"); do
            user_id="${USER_ID_BY_TOPIC_AND_MEMBER["${topic_name}:${member_number}"]}"
            liked_post_sequence=$((member_number + 6))
            saved_post_sequence=$((member_number + 9))
            create_interaction "$user_id" "$(get_post_id "$topic_name" "$liked_post_sequence")" LIKE
            create_interaction "$user_id" "$(get_post_id "$topic_name" "$saved_post_sequence")" SAVE
        done
    done
}

create_share_and_exploration_interaction_wave() {
    local topic_name
    local member_number
    local user_id
    local shared_post_sequence
    local adjacent_topic_name

    for topic_name in "${TOPIC_NAMES[@]}"; do
        for member_number in $(seq 1 "$USERS_PER_TOPIC"); do
            user_id="${USER_ID_BY_TOPIC_AND_MEMBER["${topic_name}:${member_number}"]}"
            shared_post_sequence=$((member_number + 12))
            create_interaction "$user_id" "$(get_post_id "$topic_name" "$shared_post_sequence")" SHARE

            if (( member_number == 1 )); then
                adjacent_topic_name=$(get_adjacent_topic_name "$topic_name")
                create_interaction "$user_id" "$(get_post_id "$adjacent_topic_name" 16)" VIEW
            fi
        done
    done
}

user_has_interacted_with_post() {
    local user_id="$1"
    local post_id="$2"
    local interacted_post_ids="${INTERACTED_POST_IDS_BY_USER["$user_id"]}"
    [[ ",${interacted_post_ids}" == *",${post_id},"* ]]
}

feed_meets_quality_threshold() {
    local user_id="$1"
    local required_primary_topic_recommendation_count="$2"
    local feed_response
    local user_topic_name="${USER_TOPIC_BY_ID["$user_id"]}"
    local recommended_post_id
    local recommended_post_topic_name
    local primary_topic_recommendation_count=0
    local has_interacted_post=false
    local recommended_post_count=0

    if ! feed_response=$(curl --fail-with-body --silent --show-error \
        "$RECOMMENDATION_MANAGER_BASE_URL/api/v1/recommendations/$user_id?limit=$TOP_FIVE_RECOMMENDATIONS"); then
        echo "Unable to retrieve feed for user $user_id" >&2
        return 1
    fi

    while IFS= read -r recommended_post_id; do
        [[ -z "$recommended_post_id" ]] && continue
        ((recommended_post_count += 1))
        recommended_post_topic_name="${POST_TOPIC_BY_ID["$recommended_post_id"]:-unknown}"
        if [[ "$recommended_post_topic_name" == "$user_topic_name" ]]; then
            ((primary_topic_recommendation_count += 1))
        fi
        if user_has_interacted_with_post "$user_id" "$recommended_post_id"; then
            has_interacted_post=true
        fi
    done < <(jq --raw-output '.items[].postId' <<<"$feed_response")

    if (( recommended_post_count == TOP_FIVE_RECOMMENDATIONS )) \
        && (( primary_topic_recommendation_count >= required_primary_topic_recommendation_count )) \
        && [[ "$has_interacted_post" == false ]]; then
        echo "Validated ${user_topic_name} feed for user ${user_id}: ${primary_topic_recommendation_count}/${TOP_FIVE_RECOMMENDATIONS} primary-topic posts"
        return 0
    fi

    echo "Feed quality check failed for user $user_id. Expected at least ${required_primary_topic_recommendation_count} ${user_topic_name} posts and no interacted posts. Feed: $feed_response" >&2
    return 1
}

wait_for_all_feed_quality_checks() {
    local interaction_wave_name="$1"
    local required_primary_topic_recommendation_count="$2"
    local elapsed_seconds=0
    local user_id
    local all_users_passed

    while (( elapsed_seconds < MAXIMUM_WAIT_SECONDS )); do
        all_users_passed=true
        for user_id in "${USER_ID_BY_TOPIC_AND_MEMBER[@]}"; do
            if ! feed_meets_quality_threshold "$user_id" "$required_primary_topic_recommendation_count"; then
                all_users_passed=false
            fi
        done
        if [[ "$all_users_passed" == true ]]; then
            echo "Validated all feeds after ${interaction_wave_name}."
            return 0
        fi
        sleep "$POLL_INTERVAL_SECONDS"
        ((elapsed_seconds += POLL_INTERVAL_SECONDS))
    done

    echo "Timed out waiting for feed quality after ${interaction_wave_name}." >&2
    return 1
}

build_and_start_e2e_stack() {
    CONTENT_MANAGER_PORT=18080 USER_MANAGER_PORT=18081 RECOMMENDATION_MANAGER_PORT=18082 \
        compose_command build user-manager
    CONTENT_MANAGER_PORT=18080 USER_MANAGER_PORT=18081 RECOMMENDATION_MANAGER_PORT=18082 \
        compose_command build content-manager
    CONTENT_MANAGER_PORT=18080 USER_MANAGER_PORT=18081 RECOMMENDATION_MANAGER_PORT=18082 \
        compose_command build recommendation-manager embedding-stub
    CONTENT_MANAGER_PORT=18080 USER_MANAGER_PORT=18081 RECOMMENDATION_MANAGER_PORT=18082 \
        compose_command up --detach
}

main() {
    trap cleanup_e2e_stack EXIT

    if [[ "${E2E_SKIP_STACK_START:-false}" != true ]]; then
        build_and_start_e2e_stack
    fi
    wait_for_http_server "user-manager" "$USER_MANAGER_BASE_URL/api/v1/users/signup"
    wait_for_http_server "content-manager" "$CONTENT_MANAGER_BASE_URL/api/v1/posts"
    wait_for_http_server "recommendation-manager" "$RECOMMENDATION_MANAGER_BASE_URL/api/v1/recommendations/00000000-0000-0000-0000-000000000000"

    create_social_users
    create_community_posts

    create_view_interaction_wave
    wait_for_all_feed_quality_checks "view interaction wave" 3

    create_like_and_save_interaction_wave
    wait_for_all_feed_quality_checks "like and save interaction wave" 4

    create_share_and_exploration_interaction_wave
    wait_for_all_feed_quality_checks "share and exploration interaction wave" 4

    echo "Validated social-feed simulation: 12 users, 72 posts, and three progressive interaction waves."
}

main "$@"
