FROM python:3.11-slim

WORKDIR /mlflow

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

RUN pip install --no-cache-dir mlflow psycopg2-binary

EXPOSE 5000
