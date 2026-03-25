from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_feed_service
from app.api.schemas.feed import (
    FeedCommentCreateRequest,
    FeedCommentResponse,
    FeedPersonResponse,
    FeedPostResponse,
    FeedPublishRequest,
    FeedReadEventRequest,
)
from app.services.feed_service import (
    FeedBookNotFoundError,
    FeedInputError,
    FeedPersistenceError,
    FeedPostNotFoundError,
    FeedService,
)

router = APIRouter(tags=["feed"])


@router.post(
    "/feed/publish",
    response_model=FeedPostResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Publish a saved autobiography version to the feed",
)
def publish_feed_post(
    request: FeedPublishRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    feed_service: Annotated[FeedService, Depends(get_feed_service)],
) -> FeedPostResponse:
    try:
        return feed_service.publish_post(user_id=user_id, request=request)
    except FeedBookNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Book version not found.",
        ) from exc
    except FeedInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except FeedPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to publish feed post.",
        ) from exc


@router.get(
    "/feed",
    response_model=list[FeedPostResponse],
    summary="List recommended feed posts",
)
def list_feed_posts(
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    feed_service: Annotated[FeedService, Depends(get_feed_service)],
    query_text: str | None = Query(default=None, alias="query"),
) -> list[FeedPostResponse]:
    try:
        return feed_service.list_posts(user_id=user_id, query_text=query_text)
    except FeedPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to load feed posts.",
        ) from exc


@router.get(
    "/feed/people/recommended",
    response_model=list[FeedPersonResponse],
    summary="List recommended people to connect with",
)
def list_recommended_people(
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    feed_service: Annotated[FeedService, Depends(get_feed_service)],
    query_text: str | None = Query(default=None, alias="query"),
) -> list[FeedPersonResponse]:
    try:
        return feed_service.list_people(user_id=user_id, query_text=query_text)
    except FeedPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to load recommended people.",
        ) from exc


@router.get(
    "/feed/{post_id}",
    response_model=FeedPostResponse,
    summary="Get one feed post",
)
def get_feed_post(
    post_id: UUID,
    _: Annotated[UUID, Depends(get_current_user_id)],
    feed_service: Annotated[FeedService, Depends(get_feed_service)],
) -> FeedPostResponse:
    try:
        return feed_service.get_post(post_id=post_id)
    except FeedPostNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Feed post not found.",
        ) from exc
    except FeedPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to load feed post.",
        ) from exc


@router.get(
    "/feed/{post_id}/comments",
    response_model=list[FeedCommentResponse],
    summary="List comments for a feed post",
)
def list_feed_comments(
    post_id: UUID,
    _: Annotated[UUID, Depends(get_current_user_id)],
    feed_service: Annotated[FeedService, Depends(get_feed_service)],
) -> list[FeedCommentResponse]:
    try:
        return feed_service.list_comments(post_id=post_id)
    except FeedPostNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Feed post not found.",
        ) from exc
    except FeedPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to load comments.",
        ) from exc


@router.post(
    "/feed/{post_id}/comments",
    response_model=FeedCommentResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a comment on a feed post",
)
def create_feed_comment(
    post_id: UUID,
    request: FeedCommentCreateRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    feed_service: Annotated[FeedService, Depends(get_feed_service)],
) -> FeedCommentResponse:
    try:
        return feed_service.create_comment(
            user_id=user_id,
            post_id=post_id,
            request=request,
        )
    except FeedPostNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Feed post not found.",
        ) from exc
    except FeedPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create comment.",
        ) from exc


@router.post(
    "/feed/{post_id}/read",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Record reading behavior for a feed post",
)
def record_feed_read_event(
    post_id: UUID,
    request: FeedReadEventRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    feed_service: Annotated[FeedService, Depends(get_feed_service)],
) -> Response:
    try:
        feed_service.record_read_event(
            user_id=user_id,
            post_id=post_id,
            request=request,
        )
    except FeedPostNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Feed post not found.",
        ) from exc
    except FeedPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to record reading event.",
        ) from exc

    return Response(status_code=status.HTTP_204_NO_CONTENT)
