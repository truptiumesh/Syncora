import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
} from 'lucide-react'
import { WorkspaceHeader } from '../components/WorkspaceHeader'
import { listTasksForProjects, listVisibleProjects, type WorkspaceProject } from '../services/workspaceDataService'
import { workspaceApi } from '../services/workspaceService'
import type { TaskResponse } from '../types/task'
import { apiErrorDetails } from '../utils/apiError'

type CalendarView = 'month' | 'week' | 'day'

function atMidnight(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function startOfWeek(date: Date) {
  const start = atMidnight(date)
  const weekday = (start.getDay() + 6) % 7
  start.setDate(start.getDate() - weekday)
  return start
}

function addDays(date: Date, count: number) {
  const next = new Date(date)
  next.setDate(next.getDate() + count)
  return next
}

function taskDate(task: TaskResponse) {
  return task.dueDate ? new Date(`${task.dueDate}T00:00:00`) : null
}

function dateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function displayRange(view: CalendarView, date: Date) {
  if (view === 'day') return date.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })
  if (view === 'week') {
    const start = startOfWeek(date)
    const end = addDays(start, 6)
    return `${start.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} – ${end.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}`
  }
  return date.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

function dueTone(task: TaskResponse, todayKey: string) {
  if (task.status === 'DONE') return 'calendar-event-done'
  if (task.dueDate && task.dueDate < todayKey) return 'calendar-event-overdue'
  return `calendar-event-${task.priority.toLowerCase()}`
}

export function CalendarPage() {
  const [date, setDate] = useState(() => atMidnight(new Date()))
  const [view, setView] = useState<CalendarView>('month')
  const [projects, setProjects] = useState<WorkspaceProject[]>([])
  const [tasks, setTasks] = useState<TaskResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function loadCalendar() {
    setLoading(true)
    setError('')
    try {
      const workspaces = await workspaceApi.list()
      const projectResult = await listVisibleProjects(workspaces)
      const taskResult = await listTasksForProjects(projectResult.projects)
      setProjects(projectResult.projects)
      setTasks(taskResult.taskSets.flatMap(({ tasks: projectTasks }) => projectTasks))
      const failures = projectResult.failures + taskResult.failures
      if (failures) setError(`${[...projectResult.failureDetails, ...taskResult.failureDetails].join(' ')} Displaying available task data.`)
    } catch (requestError) {
      setError(apiErrorDetails(requestError, 'Calendar data could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void loadCalendar() }, [])

  const projectNames = useMemo(
    () => new Map(projects.map(({ project }) => [project.id, project])),
    [projects],
  )
  const events = useMemo(() => tasks.filter((task) => taskDate(task) !== null), [tasks])
  const todayKey = dateKey(atMidnight(new Date()))
  const start = view === 'month'
    ? startOfWeek(new Date(date.getFullYear(), date.getMonth(), 1))
    : view === 'week' ? startOfWeek(date) : atMidnight(date)
  const dayCount = view === 'month' ? 42 : view === 'week' ? 7 : 1
  const days = Array.from({ length: dayCount }, (_, index) => addDays(start, index))
  const visibleEvents = (day: Date) => events.filter((task) => task.dueDate === dateKey(day))

  function move(direction: number) {
    setDate((current) => {
      if (view === 'month') return new Date(current.getFullYear(), current.getMonth() + direction, 1)
      if (view === 'week') return addDays(current, direction * 7)
      return addDays(current, direction)
    })
  }

  function eventCard(task: TaskResponse, compact = false) {
    return (
      <Link
        key={task.id}
        className={`calendar-event ${dueTone(task, todayKey)}${compact ? ' calendar-event-compact' : ''}`}
        to={`/tasks/${task.id}`}
        title={`${task.taskKey}: ${task.title}`}
      >
        <span className="calendar-event-time">{task.taskKey}</span>
        <strong>{task.title}</strong>
        {!compact && <small>{projectNames.get(task.projectId)?.name ?? 'Project'} · {task.priority}</small>}
      </Link>
    )
  }

  return (
    <main className="workspace-page app-directory-page">
      <WorkspaceHeader />
      <section className="workspace-content calendar-page-content">
        <header className="calendar-page-heading">
          <div><span className="eyebrow">PLAN YOUR NEXT MOVE</span><h1>Calendar</h1><p>Due dates from your accessible project tasks.</p></div>
          <div className="calendar-view-switch" role="group" aria-label="Calendar view">
            {(['month', 'week', 'day'] as const).map((value) => <button key={value} type="button" aria-pressed={view === value} onClick={() => setView(value)}>{value[0].toUpperCase() + value.slice(1)}</button>)}
          </div>
        </header>
        <div className="calendar-toolbar">
          <div><button className="calendar-nav-button" type="button" aria-label="Previous period" onClick={() => move(-1)}><ChevronLeft size={17} /></button><button className="calendar-nav-button" type="button" aria-label="Next period" onClick={() => move(1)}><ChevronRight size={17} /></button><h2>{displayRange(view, date)}</h2></div>
          <button className="button-outline calendar-today-button" type="button" onClick={() => setDate(atMidnight(new Date()))}><CalendarDays size={15} /> Today</button>
        </div>
        {error && <div className="widget-error" role="alert"><p>{error}</p><button className="button-outline" type="button" onClick={() => void loadCalendar()}>Retry</button></div>}
        {loading ? <div className="workspace-loading" role="status"><span className="spinner" />Loading calendar...</div> : (
          <>
            <section className={`calendar-grid calendar-${view}`} aria-label={`${view} calendar`}>
              {view !== 'day' && <div className="calendar-weekdays">{['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((day) => <span key={day}>{day}</span>)}</div>}
              <div className="calendar-days">
                {days.map((day) => {
                  const key = dateKey(day)
                  const isCurrentMonth = day.getMonth() === date.getMonth()
                  const dayEvents = visibleEvents(day)
                  return (
                    <article key={key} className={`calendar-day${isCurrentMonth ? '' : ' calendar-day-outside'}${key === todayKey ? ' calendar-day-today' : ''}`}>
                      <button className="calendar-day-number" type="button" aria-label={`Show ${day.toLocaleDateString()}`} onClick={() => { setDate(day); setView('day') }}>
                        {day.toLocaleDateString(undefined, { day: 'numeric', ...(view === 'day' ? { weekday: 'long', month: 'long' } : {}) })}
                      </button>
                      <div className="calendar-day-events">
                        {dayEvents.slice(0, view === 'month' ? 3 : 100).map((task) => eventCard(task, view === 'month'))}
                        {view === 'month' && dayEvents.length > 3 && <button className="calendar-more-events" type="button" onClick={() => { setDate(day); setView('day') }}>+{dayEvents.length - 3} more</button>}
                        {view !== 'month' && dayEvents.length === 0 && <span className="calendar-no-events">No due tasks</span>}
                      </div>
                    </article>
                  )
                })}
              </div>
            </section>
            {events.length === 0 && <div className="calendar-empty"><Clock3 size={19} /><span>No tasks with due dates were found in accessible projects.</span></div>}
            <div className="calendar-legend" aria-label="Priority legend">
              <span><i className="legend-urgent" />Urgent</span><span><i className="legend-high" />High</span><span><i className="legend-medium" />Medium</span><span><i className="legend-low" />Low</span><span><i className="legend-done" />Completed</span>
            </div>
          </>
        )}
      </section>
    </main>
  )
}
