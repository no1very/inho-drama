package api

import (
	"net/http"
	"strconv"

	"nasdrama/server/internal/store"
)

func (s *Server) handleFeed(w http.ResponseWriter, r *http.Request, u *store.User) {
	q := r.URL.Query()
	cursor, _ := strconv.Atoi(q.Get("cursor"))
	limit, _ := strconv.Atoi(q.Get("limit"))
	if cursor < 0 {
		cursor = 0
	}
	if limit <= 0 {
		limit = 12
	}
	if limit > 50 {
		limit = 50
	}
	list, more, err := s.opts.Store.ListFeed(cursor, limit)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "查询推荐流失败")
		return
	}
	items := make([]SeriesCardDTO, 0, len(list))
	for i := range list {
		items = append(items, s.seriesCard(&list[i]))
	}
	resp := FeedResponse{Items: items}
	if more {
		n := int64(cursor + limit)
		resp.NextCursor = &n
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleSeriesList(w http.ResponseWriter, r *http.Request, u *store.User) {
	q := r.URL.Query()
	page, _ := strconv.Atoi(q.Get("page"))
	if page <= 0 {
		page = 1
	}
	pageSize, _ := strconv.Atoi(q.Get("page_size"))
	if pageSize <= 0 {
		pageSize = 20
	}
	if pageSize > 100 {
		pageSize = 100
	}
	list, total, err := s.opts.Store.SearchSeries(q.Get("keyword"), (page-1)*pageSize, pageSize)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "查询剧集失败")
		return
	}
	items := make([]SeriesCardDTO, 0, len(list))
	for i := range list {
		items = append(items, s.seriesCard(&list[i]))
	}
	writeJSON(w, http.StatusOK, SeriesListResponse{Items: items, Total: total, Page: page, PageSize: pageSize})
}

func (s *Server) handleSeriesDetail(w http.ResponseWriter, r *http.Request, u *store.User) {
	id, err := pathID(r)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "无效的剧集 ID")
		return
	}
	series, err := s.opts.Store.GetSeries(id)
	if err != nil {
		writeErr(w, http.StatusNotFound, "剧集不存在")
		return
	}
	eps, err := s.opts.Store.ListEpisodes(id)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "查询单集失败")
		return
	}
	states, err := s.opts.Store.SeriesStates(u.ID, id)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "查询进度失败")
		return
	}
	episodes := make([]EpisodeDTO, 0, len(eps))
	for i := range eps {
		episodes = append(episodes, episodeDTO(&eps[i]))
	}
	stateDTOs := make([]EpisodeStateDTO, 0, len(states))
	for _, st := range states {
		stateDTOs = append(stateDTOs, EpisodeStateDTO{
			EpisodeID: st.EpisodeID,
			Index:     st.Index,
			Position:  st.Position,
			Duration:  floatPtr(st.Duration),
			Completed: st.Completed,
			UpdatedAt: st.UpdatedAt,
		})
	}
	detail := SeriesDetailDTO{
		Series:         s.seriesCard(series),
		Episodes:       episodes,
		States:         stateDTOs,
	}
	cont, err := s.opts.Store.ContinueEpisode(u.ID, id)
	if err == nil {
		detail.ContinueEpisodeID = &cont.ID
	}
	writeJSON(w, http.StatusOK, detail)
}

func (s *Server) handleProgress(w http.ResponseWriter, r *http.Request, u *store.User) {
	var req ProgressRequest
	if err := readJSON(w, r, &req); err != nil {
		writeErr(w, http.StatusBadRequest, "请求体格式错误")
		return
	}
	if req.EpisodeID <= 0 {
		writeErr(w, http.StatusBadRequest, "缺少 episode_id")
		return
	}
	// 微量进度不落库：误点进播放页立刻退出、切集瞬间的 0 位置上报等
	// 都不会再产生「继续观看」记录（看完的 completed 上报不受影响）。
	if !req.Completed && req.Position < 5 {
		writeOK(w)
		return
	}
	seriesID, idx, err := s.opts.Store.EpisodeIndex(req.EpisodeID)
	if err != nil {
		writeErr(w, http.StatusNotFound, "单集不存在")
		return
	}
	if err := s.opts.Store.SaveState(u.ID, seriesID, idx, req.Position, req.Duration, req.Completed); err != nil {
		writeErr(w, http.StatusInternalServerError, "保存进度失败")
		return
	}
	writeOK(w)
}

func (s *Server) handleHistory(w http.ResponseWriter, r *http.Request, u *store.User) {
	// finished=0：只返回未看完的记录（首页「继续观看」用），默认返回全部
	unfinishedOnly := r.URL.Query().Get("finished") == "0"
	list, err := s.opts.Store.History(u.ID, 200, unfinishedOnly)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "查询历史失败")
		return
	}
	items := make([]HistoryItemDTO, 0, len(list))
	for _, it := range list {
		items = append(items, HistoryItemDTO{
			Series:    s.seriesCard(&it.Series),
			Episode:   episodeDTO(&it.Episode),
			Position:  it.Position,
			Duration:  floatPtr(it.Duration),
			Completed: it.Completed,
			UpdatedAt: it.UpdatedAt,
		})
	}
	writeJSON(w, http.StatusOK, HistoryResponse{Items: items})
}

func (s *Server) handleDeleteHistory(w http.ResponseWriter, r *http.Request, u *store.User) {
	id, err := pathID(r)
	if err != nil {
		writeErr(w, http.StatusBadRequest, "无效的剧集 ID")
		return
	}
	if err := s.opts.Store.ClearHistory(u.ID, id); err != nil {
		writeErr(w, http.StatusInternalServerError, "删除记录失败")
		return
	}
	writeOK(w)
}

func pathID(r *http.Request) (int64, error) {
	return strconv.ParseInt(r.PathValue("id"), 10, 64)
}
