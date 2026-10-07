FROM python:3.12-slim
RUN useradd --uid 10001 --create-home lumi
WORKDIR /app
COPY --chown=lumi:lumi app.py provider.py catalog.json provision.py ./
RUN mkdir runtime && chown lumi:lumi runtime
USER lumi
ENV HOST=0.0.0.0 PORT=8080
EXPOSE 8080
CMD ["python", "app.py"]
