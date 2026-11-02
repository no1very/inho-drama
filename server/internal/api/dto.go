package api

import (
	"fmt"

	"nasdrama/server/internal/store"
)

type UserDTO struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
}

type AuthResponse struct {
	Token string  `json:"token"`
	User  UserDTO `json:"user"`
}

type ServerInfoDTO struct {
	Name          string `json:"name"`
	Version       string `json:"version"`
	AllowRegister bool   `json:"allow_register"`
}

type SeriesCardDTO struct {
	ID           int64  `json:"id"`
	Title        string `json:"title"`
	Intro        string `json:"intro"`
	Category     string `json:"category"`
	Cover        string `json:"cover"`
	EpisodeCount int64  `json:"episode_count"`
	SourceName   string `json:"source_name"`
	UpdatedAt    int64  `json:"updated_at"`
}

type EpisodeDTO struct {
	ID        int64    `json:"id"`
	SeriesID  int64    `json:"series_id"`
	Index     int64    `json:"index"`
	Title     string   `json:"title"`
	Duration  *float64 `json:"duration"`
	Size      *int64   `json:"size"`
	Container string   `json:"container"`
}

type EpisodeStateDTO struct {
	EpisodeID int64    `json:"episode_id"`
	Index     int64    `json:"index"`
	Position  float64  `json:"position"`
	Duration  *float64 `json:"duration"`
	Completed bool     `json:"completed"`
	UpdatedAt int64    `json:"updated_at"`
}

type SeriesDetailDTO struct {
	Series            SeriesCardDTO     `json:"series"`
	Episodes          []EpisodeDTO      `json:"episodes"`
	ContinueEpisodeID *int64            `json:"continue_episode_id"`
	States            []EpisodeStateDTO `json:"episode_states"`
}

type FeedResponse struct {
	Items      []SeriesCardDTO `json:"items"`
	NextCursor *int64          `json:"next_cursor"`
}

type SeriesListResponse struct {
	Items    []SeriesCardDTO `json:"items"`
	Total    int64           `json:"total"`
	Page     int             `json:"page"`
	PageSize int             `json:"page_size"`
}

type ProgressRequest struct {
	SeriesID  int64   `json:"series_id"`
	EpisodeID int64   `json:"episode_id"`
	Position  float64 `json:"position"`
	Duration  float64 `json:"duration"`
	Completed bool    `json:"completed"`
}

type HistoryItemDTO struct {
	Series    SeriesCardDTO `json:"series"`
	Episode   EpisodeDTO    `json:"episode"`
	Position  float64       `json:"position"`
	Duration  *float64      `json:"duration"`
	Completed bool          `json:"completed"`
	UpdatedAt int64         `json:"updated_at"`
}

type HistoryResponse struct {
	Items []HistoryItemDTO `json:"items"`
}

func (s *Server) seriesCard(v *store.Series) SeriesCardDTO {
	return SeriesCardDTO{
		ID:           v.ID,
		Title:        v.Title,
		Intro:        v.Intro,
		Category:     v.Category,
		Cover:        fmt.Sprintf("/api/v1/cover/%d", v.ID),
		EpisodeCount: v.EpisodeCount,
		SourceName:   v.SourceName,
		UpdatedAt:    v.UpdatedAt,
	}
}

func episodeDTO(v *store.Episode) EpisodeDTO {
	d := EpisodeDTO{
		ID:        v.ID,
		SeriesID:  v.SeriesID,
		Index:     v.Index,
		Title:     v.Title,
		Container: v.Container,
	}
	if v.Duration >= 0 {
		f := v.Duration
		d.Duration = &f
	}
	if v.Size >= 0 {
		s := v.Size
		d.Size = &s
	}
	return d
}

func floatPtr(f float64) *float64 {
	if f < 0 {
		return nil
	}
	return &f
}
