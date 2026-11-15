from fastapi import Request

from ai_service.container import Services


def get_services(request: Request) -> Services:
    svc: Services = request.app.state.services
    return svc
