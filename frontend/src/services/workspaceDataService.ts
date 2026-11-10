import { projectApi } from './projectService'
import { taskApi } from './taskService'
import type { ProjectResponse } from '../types/project'
import type { TaskResponse } from '../types/task'
import type { WorkspaceResponse } from '../types/workspace'
import { apiErrorDetails } from '../utils/apiError'

export interface WorkspaceProject {
  project: ProjectResponse
  workspaceName: string
}

export interface ProjectTaskSet {
  projectId: string
  tasks: TaskResponse[]
}

export async function listVisibleProjects(workspaces: WorkspaceResponse[]) {
  const projectResults = await Promise.allSettled(workspaces.map(async (workspace) => {
    const projects: ProjectResponse[] = []
    let page = 0
    let last = false
    while (!last) {
      const result = await projectApi.list(workspace.id, { page, size: 100 })
      projects.push(...result.content)
      last = result.last
      page += 1
    }
    return projects.map((project) => ({ project, workspaceName: workspace.name }))
  }))
  const failureDetails = projectResults.flatMap((result, index) => result.status === 'rejected'
    ? [`${workspaces[index].name}: ${apiErrorDetails(result.reason, 'Projects could not be loaded.')}`]
    : [])
  return {
    projects: projectResults.flatMap((result) => result.status === 'fulfilled' ? result.value : []),
    failures: failureDetails.length,
    failureDetails,
  }
}

export async function listTasksForProjects(projects: WorkspaceProject[]) {
  const taskResults = await Promise.allSettled(projects.map(async ({ project }) => {
    const tasks: TaskResponse[] = []
    let page = 0
    let last = false
    while (!last) {
      const result = await taskApi.list(project.id, { page, size: 100 })
      tasks.push(...result.content)
      last = result.last
      page += 1
    }
    return { projectId: project.id, tasks }
  }))
  const failureDetails = taskResults.flatMap((result, index) => result.status === 'rejected'
    ? [`${projects[index].project.name}: ${apiErrorDetails(result.reason, 'Tasks could not be loaded.')}`]
    : [])
  return {
    taskSets: taskResults.flatMap((result) => result.status === 'fulfilled' ? [result.value] : []),
    failures: failureDetails.length,
    failureDetails,
  }
}
